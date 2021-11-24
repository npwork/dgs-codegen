/*
 *
 *  Copyright 2020 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.netflix.graphql.dgs.codegen.client

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode
import com.netflix.graphql.dgs.codegen.CodeGen
import com.netflix.graphql.dgs.codegen.CodeGenConfig
import com.netflix.graphql.dgs.codegen.Language
import com.netflix.graphql.dgs.codegen.assertCompilesKotlin
import com.netflix.graphql.dgs.codegen.generators.kotlin.DgsDslContext
import com.squareup.kotlinpoet.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/*
 *
 *  Copyright 2020 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

class KotlinClientApiGenQueryTest {
    companion object {
        const val ROOT_QUERY_PROJECTION_NAME = "QueryProjection"
        const val PEOPLE_PROJECTION_NAME = "PersonProjection"
        const val ADDRESS_PROJECTION_NAME = "AddressProjection"
        const val BASE_PACKAGE = "com.netflix.graphql.dgs.codegen.tests.generated"
        const val CLIENT_PACKAGE = "$BASE_PACKAGE.client"
    }

    @Test
    fun `simple projection`() {
        val schema = """
            type Query {
                people: [Person]
            }
            
            type Person {
                firstname: String
                lastname: String
            }
        """.trimIndent()

        verifyProjections(
            schema = schema,
            queryFunctions = listOf(
                functionInnerProjection(
                    ROOT_QUERY_PROJECTION_NAME,
                    "people",
                    ClassName(CLIENT_PACKAGE, PEOPLE_PROJECTION_NAME)
                ),
            ),
            projections = listOf(
                Projection(
                    className = PEOPLE_PROJECTION_NAME,
                    expectedFunctions = listOf(funPrimitive("firstname"), funPrimitive("lastname"))
                )
            )
        )
    }

    @Test
    fun `with comment`() {
        val schema = """
            type Query {
                ""${'"'}
                All the people
                ""${'"'}
                people: [Person]
            }
            
            type Person {
                firstname: String
                lastname: String
            }           
        """.trimIndent()

        verifyProjections(
            schema = schema,
            queryFunctions = listOf(
                functionInnerProjection(
                    ROOT_QUERY_PROJECTION_NAME,
                    "people",
                    ClassName(CLIENT_PACKAGE, PEOPLE_PROJECTION_NAME)
                ),
            ),
            projections = listOf(
                Projection(
                    className = PEOPLE_PROJECTION_NAME,
                    expectedFunctions = listOf(funPrimitive("firstname"), funPrimitive("lastname"))
                )
            )
        )
    }

    @Test
    fun `with nested projection`() {
        val schema = """
            type Query {
                people: [Person]
            }
            
            type Person {
                firstname: String
                lastname: String
                address: Address
            }
            
            type Address {
                street: String
                house: Int
            }
        """.trimIndent()

        verifyProjections(
            schema = schema,
            queryFunctions = listOf(
                functionInnerProjection(
                    ROOT_QUERY_PROJECTION_NAME,
                    "people",
                    ClassName(CLIENT_PACKAGE, PEOPLE_PROJECTION_NAME)
                ),
            ),
            projections = listOf(
                Projection(
                    className = PEOPLE_PROJECTION_NAME,
                    expectedFunctions = listOf(
                        functionInnerProjection(
                            PEOPLE_PROJECTION_NAME,
                            "address",
                            ClassName(CLIENT_PACKAGE, ADDRESS_PROJECTION_NAME)
                        ),
                        funPrimitive("firstname"),
                        funPrimitive("lastname"),
                    )
                ),
                Projection(
                    ADDRESS_PROJECTION_NAME,
                    expectedFunctions = listOf(funPrimitive("street"), funPrimitive("house"),)
                )
            )
        )
    }

    private data class Projection(val className: String, val expectedFunctions: List<FunSpec>)

    private fun verifyProjections(schema: String, queryFunctions: List<FunSpec>, projections: List<Projection>) {
        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = BASE_PACKAGE,
                language = Language.KOTLIN,
                generateClientApi = true,
            )
        ).generate()

        // Query
        assertThat(codeGenResult.kotlinQueryTypes.size).isEqualTo(1)
        verifyProjection(
            codeGenResult.kotlinQueryTypes[0],
            className = ROOT_QUERY_PROJECTION_NAME,
            expectedFunctions = queryFunctions
        )

        assertThat(codeGenResult.kotlinClientProjections).hasSize(projections.size)

        projections.forEachIndexed { idx, it ->
            val current = codeGenResult.kotlinClientProjections[idx]
            assertThat(current.name).isEqualTo(it.className)

            verifyProjection(
                current,
                className = it.className,
                expectedFunctions = it.expectedFunctions
            )
        }

        assertCompilesKotlin(codeGenResult.kotlinClientProjections + codeGenResult.kotlinQueryTypes)
    }

    private fun verifyProjection(projection: FileSpec, className: String, expectedFunctions: List<FunSpec>) {
        val members = getMembersAsListOfTypeSpec(projection)

        assertThat(members).hasSize(1)

        assertThat(members[0].name).isEqualTo(className)
        assertThat(members[0].superclass).isEqualTo(BaseProjectionNode::class.asTypeName())

        assertThat(members[0].annotationSpecs).containsExactlyInAnyOrder(
            AnnotationSpec.builder(DgsDslContext::class).build()
        )

        assertThat(members[0].funSpecs).containsExactlyInAnyOrder(*expectedFunctions.toTypedArray())
    }

    private fun functionInnerProjection(className: String, fieldName: String, subProjectionType: ClassName): FunSpec {
        return FunSpec.builder(fieldName)
            .addParameter(
                ParameterSpec
                    .builder("initBlock", LambdaTypeName.get(receiver = subProjectionType, returnType = UNIT))
                    .build()
            )
            .returns(subProjectionType)
            .addCode("return %T().apply {\n", subProjectionType)
            .addStatement("this@%T.fields[%S] = this", ClassName("", className), fieldName)
            .addStatement("initBlock()")
            .addCode("}")
            .build()
    }

    private fun funPrimitive(fieldName: String): FunSpec {
        return FunSpec.builder(fieldName)
            .addStatement("fields[%S] = null", fieldName)
            .build()
    }

    private fun getMembersAsListOfTypeSpec(fileSpec: FileSpec): List<TypeSpec> {
        return (fileSpec.members as List<TypeSpec>)
    }
}
