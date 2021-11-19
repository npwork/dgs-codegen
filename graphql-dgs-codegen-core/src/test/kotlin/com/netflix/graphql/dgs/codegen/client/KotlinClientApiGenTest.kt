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

class KotlinClientApiGenTest {
    companion object {
        const val PEOPLE_PROJECTION_NAME = "PeopleProjection"
        const val ADDRESS_PROJECTION_NAME = "AddressProjection"
        const val BASE_PACKAGE = "com.netflix.graphql.dgs.codegen.tests.generated"
        const val CLIENT_PACKAGE = "$BASE_PACKAGE.client"
    }

    @Test
    fun `one level projection`() {
        val schema = """
            type Query {
                people: [Person]
            }
            
            type Person {
                firstname: String
                lastname: String
            }
        """.trimIndent()

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = BASE_PACKAGE,
                language = Language.KOTLIN,
                generateClientApi = true,
            )
        ).generate()

        assertThat(codeGenResult.kotlinQueryTypes.size).isEqualTo(1)
        //assertThat(codeGenResult.kotlinQueryTypes[0].name).isEqualTo("PeopleGraphQLQuery")


        assertThat(codeGenResult.kotlinClientProjections).hasSize(1)
        assertThat(codeGenResult.kotlinClientProjections.first().name).isEqualTo(PEOPLE_PROJECTION_NAME)

        val peopleProjection = codeGenResult.kotlinClientProjections[0]

        verifyProjection(
            peopleProjection,
            className = PEOPLE_PROJECTION_NAME,
            expectedFunctions = listOf(
                simpleTypeProjectionFun("firstname"),
                simpleTypeProjectionFun("lastname"),
            )
        )

        /*assertThat(codeGenResult.kotlinClientProjections.first().typeSpec.name).isEqualTo("PeopleProjection")
        assertThat(codeGenResult.kotlinClientProjections.first().typeSpec.).isEqualTo("PeopleProjection")*/

        //assertCompilesKotlin(codeGenResult.kotlinClientProjections + codeGenResult.kotlinQueryTypes)
    }

    @Test
    fun `nested projection`() {
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

        val codeGenResult = CodeGen(
            CodeGenConfig(
                schemas = setOf(schema),
                packageName = BASE_PACKAGE,
                language = Language.KOTLIN,
                generateClientApi = true,
            )
        ).generate()

        assertThat(codeGenResult.kotlinClientProjections).hasSize(2)

        val peopleProjection = codeGenResult.kotlinClientProjections[0]
        val addressProjection = codeGenResult.kotlinClientProjections[1]

        assertThat(peopleProjection.name).isEqualTo(PEOPLE_PROJECTION_NAME)
        assertThat(addressProjection.name).isEqualTo(ADDRESS_PROJECTION_NAME)

        verifyProjection(
            peopleProjection,
            className = PEOPLE_PROJECTION_NAME,
            expectedFunctions = listOf(
                innerProjectionFun("address", ClassName(CLIENT_PACKAGE, ADDRESS_PROJECTION_NAME)),
                simpleTypeProjectionFun("firstname"),
                simpleTypeProjectionFun("lastname"),
            )
        )

        verifyProjection(
            addressProjection,
            className = ADDRESS_PROJECTION_NAME,
            expectedFunctions = listOf(
                simpleTypeProjectionFun("street"),
                simpleTypeProjectionFun("house"),
            )
        )

        /*assertThat(codeGenResult.kotlinClientProjections.first().typeSpec.name).isEqualTo("PeopleProjection")
        assertThat(codeGenResult.kotlinClientProjections.first().typeSpec.).isEqualTo("PeopleProjection")*/

        //assertCompilesKotlin(codeGenResult.kotlinClientProjections + codeGenResult.kotlinQueryTypes)
    }

    private fun innerProjectionFun(fieldName: String, subProjectionType: ClassName): FunSpec {
        return FunSpec.builder(fieldName)
            .addParameter(
                ParameterSpec
                    .builder("initBlock", LambdaTypeName.get(receiver = subProjectionType, returnType = UNIT))
                    .build()
            )
            .returns(subProjectionType)
            .addCode("return %T().apply {\n", subProjectionType)
            .addStatement("fields[%S] = this", fieldName)
            .addStatement("initBlock()")
            .addCode("}")
            .build()
    }

    private fun simpleTypeProjectionFun(fieldName: String): FunSpec {
        return FunSpec.builder(fieldName)
            .addStatement("fields[%S] = null", fieldName)
            .build()
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

    fun getMembersAsListOfTypeSpec(fileSpec: FileSpec): List<TypeSpec> {
        return (fileSpec.members as List<TypeSpec>)
    }
}
