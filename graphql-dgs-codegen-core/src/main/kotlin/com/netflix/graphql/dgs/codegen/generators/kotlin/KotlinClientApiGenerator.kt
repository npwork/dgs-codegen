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

package com.netflix.graphql.dgs.codegen.generators.kotlin

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode
import com.netflix.graphql.dgs.codegen.*
import com.netflix.graphql.dgs.codegen.generators.java.ReservedKeywordSanitizer
import com.netflix.graphql.dgs.codegen.generators.shared.CodeGeneratorUtils.capitalized
import com.squareup.kotlinpoet.*
import graphql.language.*

class KotlinClientApiGenerator(private val config: CodeGenConfig, private val document: Document) {
    private val generatedClasses = mutableSetOf<String>()
    private val typeUtils = KotlinTypeUtils(config.packageNameTypes, config)

    fun generate(definition: ObjectTypeDefinition): CodeGenResult {
        return definition.fieldDefinitions.filterIncludedInConfig(definition.name, config).filterSkipped().map {
            val kotlinFile = createQueryBuilderClass(it, definition.name)

            val rootProjection =
                it.type.findTypeDefinition(document, true)
                    ?.let { typeDefinition -> createProjection(typeDefinition, it.name.capitalized()) }
                    ?: CodeGenResult()
            CodeGenResult(kotlinQueryTypes = listOf(kotlinFile)).merge(rootProjection)
        }.fold(CodeGenResult()) { total, current -> total.merge(current) }
    }

    private fun createQueryBuilderClass(it: FieldDefinition, operation: String): FileSpec {
        val typeSpec = TypeSpec.classBuilder("${it.name.capitalized()}GraphQLQueryBuilder")
            .build()

        val fileSpec = FileSpec.builder(getPackageName(), typeSpec.name!!).addType(typeSpec).build()

        return fileSpec
        //.addModifiers(Modifier.PUBLIC).superclass(ClassName.get(GraphQLQuery::class.java))


        /*if (fields.isNotEmpty()) {
            kotlinType.addModifiers(KModifier.DATA)
        }

        if (it.description != null) {
            javaType.addJavadoc(it.description.content.lines().joinToString("\n"))
        }*/
    }

    private fun createProjection(type: TypeDefinition<*>, prefix: String): CodeGenResult {
        val clazzName = "${prefix}Projection"
        val kotlinType = TypeSpec.classBuilder(clazzName)
            .addAnnotation(DslMarkerContext::class)
            .superclass(BaseProjectionNode::class)

        if (generatedClasses.contains(clazzName)) return CodeGenResult() else generatedClasses.add(clazzName)

        val fieldDefinitions =
            type.fieldDefinitions() + document.definitions.filterIsInstance<ObjectTypeExtensionDefinition>()
                .filter { it.name == type.name }.flatMap { it.fieldDefinitions }

        val codeGenResult = fieldDefinitions
            .filterSkipped()
            .mapNotNull {
                val typeDefinition = it.type.findTypeDefinition(
                    document,
                    excludeExtensions = true,
                    includeBaseTypes = it.inputValueDefinitions.isNotEmpty(),
                    includeScalarTypes = it.inputValueDefinitions.isNotEmpty()
                )
                if (typeDefinition != null) it to typeDefinition else null
            }
            .map { (fieldDef, typeDef) ->
                // Subprojection?????
                /*// init: MyTasks_FormProjection.() -> Unit
                val unitType = Unit::class.asClassName()
                val booleanType = Boolean::class.asClassName()
                val stringType = String::class.asClassName().copy(nullable = true)
*/
                val subProjectionType =
                    ClassName(
                        getPackageName(),
                        ReservedKeywordSanitizer.sanitize("${typeDef.name.capitalized()}Projection")
                    )

                val lambda = LambdaTypeName.get(receiver = subProjectionType, returnType = UNIT)

                kotlinType.addFunction(
                    FunSpec.builder(fieldDef.name)
                        .addParameter(
                            ParameterSpec
                                //.builder("init", String::class.asTypeName().copy(nullable = true))
                                .builder("init", lambda)
                                .build()
                        )
                        .returns(subProjectionType)
                        /**
                         * return MyTasks_FormProjection(this, this).apply {
                        fields["form"] = this
                        init()
                        }
                         */
                        .addCode("return %T().apply {", subProjectionType)
                        .addStatement("fields[%S] = this", fieldDef.name)
                        .addStatement("init()")
                        .addCode("}")
                        .build()
                )

                println("SUB PROJECTION")
               /* val projectionName = "${prefix}_${fieldDef.name.capitalized()}Projection"

                if (typeDef !is ScalarTypeDefinition) {
                    val noArgMethodBuilder = FunSpec.builder(ReservedKeywordSanitizer.sanitize(fieldDef.name))
                        //.returns(ClassName.get(getPackageName(), projectionName))
                        .returns(Unit::class.java)
                        .addCode(
                            """
                            |$projectionName projection = new $projectionName(this, this);
                            |getFields().put("${fieldDef.name}", projection);
                            |return projection;
                            """.trimMargin()
                        )
                        .addModifiers(KModifier.PUBLIC)
                    kotlinType.addFunction(noArgMethodBuilder.build())
                }

                if (fieldDef.inputValueDefinitions.isNotEmpty()) {
                    println("PARAMS?")
                    //addFieldSelectionMethodWithArguments(fieldDef, projectionName, javaType, projectionRoot = "this")
                }

                val processedEdges = mutableSetOf<Pair<String, String>>()
                processedEdges.add(typeDef.name to type.name)
                println("SUBPROJ")*/
                CodeGenResult()
                //createSubProjection(typeDef, javaType.build(), javaType.build(), "${prefix}_${fieldDef.name.capitalized()}", processedEdges, 1)
            }
            .fold(CodeGenResult()) { total, current -> total.merge(current) }

        fieldDefinitions.filterSkipped().forEach {
            val objectTypeDefinition = it.type.findTypeDefinition(document)
            // Primitive
            if (objectTypeDefinition == null) {
                kotlinType.addFunction(
                    FunSpec.builder(ReservedKeywordSanitizer.sanitize(it.name))
                        .addStatement("fields[%S] = null", it.name)
                        .build()
                )
            }
        }

        /*val concreteTypesResult = createConcreteTypes(type, javaType.build(), javaType, prefix, mutableSetOf(), 0)
        val unionTypesResult = createUnionTypes(type, javaType, javaType.build(), prefix, mutableSetOf(), 0)*/

        val typeSpec = kotlinType.build()

        val fileSpec = FileSpec.builder(getPackageName(), typeSpec.name!!).addType(typeSpec).build()

        return CodeGenResult(kotlinClientProjections = listOf(fileSpec)).merge(codeGenResult)
        //.merge(concreteTypesResult).merge(unionTypesResult)
    }

    private fun getPackageName(): String {
        return config.packageNameTypes
    }
}

@DslMarker
annotation class DslMarkerContext
