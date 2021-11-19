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
import com.netflix.graphql.dgs.codegen.generators.kotlin.DslMarkerContext
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
    private val basePackageName = "com.netflix.graphql.dgs.codegen.tests.generated"

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
                packageName = basePackageName,
                language = Language.KOTLIN,
                generateClientApi = true,
            )
        ).generate()

        assertThat(codeGenResult.kotlinQueryTypes.size).isEqualTo(1)
        //assertThat(codeGenResult.kotlinQueryTypes[0].name).isEqualTo("PeopleGraphQLQuery")


        assertThat(codeGenResult.kotlinClientProjections).hasSize(1)
        assertThat(codeGenResult.kotlinClientProjections.first().name).isEqualTo("PeopleProjection")

        val projection = codeGenResult.kotlinClientProjections[0]

        verifyProjection(
            projection,
            className = "PeopleProjection",
            functions = listOf(
                FunSpec.builder("firstname")
                    .addStatement("fields[%S] = null", "firstname")
                    .build(),
                FunSpec.builder("lastname")
                    .addStatement("fields[%S] = null", "lastname")
                    .build()
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
                packageName = basePackageName,
                language = Language.KOTLIN,
                generateClientApi = true,
            )
        ).generate()

        //assertThat(codeGenResult.kotlinQueryTypes.size).isEqualTo(1)
        //assertThat(codeGenResult.kotlinQueryTypes[0].name).isEqualTo("PeopleGraphQLQuery")


        assertThat(codeGenResult.kotlinClientProjections).hasSize(2)

        val peopleProjection = codeGenResult.kotlinClientProjections[0]
        val addressProjection = codeGenResult.kotlinClientProjections[1]

        assertThat(peopleProjection.name).isEqualTo("PeopleProjection")
        assertThat(addressProjection.name).isEqualTo("AddressProjection")

        verifyProjection(
            peopleProjection,
            className = "PeopleProjection",
            functions = listOf(
                FunSpec.builder("firstname")
                    .addStatement("fields[%S] = null", "firstname")
                    .build(),
                FunSpec.builder("lastname")
                    .addStatement("fields[%S] = null", "lastname")
                    .build()
            )
        )

        verifyProjection(
            addressProjection,
            className = "AddressProjection",
            functions = listOf(
                FunSpec.builder("street")
                    .addStatement("fields[%S] = null", "street")
                    .build(),
                FunSpec.builder("house")
                    .addStatement("fields[%S] = null", "house")
                    .build()
            )
        )

        /*assertThat(codeGenResult.kotlinClientProjections.first().typeSpec.name).isEqualTo("PeopleProjection")
        assertThat(codeGenResult.kotlinClientProjections.first().typeSpec.).isEqualTo("PeopleProjection")*/

        //assertCompilesKotlin(codeGenResult.kotlinClientProjections + codeGenResult.kotlinQueryTypes)
    }

    private fun verifyProjection(projection: FileSpec, className: String, functions: List<FunSpec>) {
        val members = getMembersAsListOfTypeSpec(projection)

        assertThat(members).hasSize(1)

        assertThat(members[0].name).isEqualTo(className)
        assertThat(members[0].superclass).isEqualTo(BaseProjectionNode::class.asTypeName())

        assertThat(members[0].annotationSpecs).containsExactly(
            AnnotationSpec.builder(DslMarkerContext::class).build()
        )

        assertThat(members[0].funSpecs).containsExactly(*functions.toTypedArray())
    }

    fun getMembersAsListOfTypeSpec(fileSpec: FileSpec): List<TypeSpec> {
        return (fileSpec.members as List<TypeSpec>)
    }
}
