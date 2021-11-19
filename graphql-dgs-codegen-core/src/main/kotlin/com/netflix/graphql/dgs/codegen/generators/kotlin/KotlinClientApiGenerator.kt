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
            val typeDef = it.type.findTypeDefinition(
                document,
                excludeExtensions = true,
                includeBaseTypes = it.inputValueDefinitions.isNotEmpty(),
                includeScalarTypes = it.inputValueDefinitions.isNotEmpty()
            )

            val rootProjection =
                it.type.findTypeDefinition(document, true)
                    ?.let { typeDefinition -> createProjection(typeDefinition, typeDef!!.name.capitalized()) }
                    ?: CodeGenResult()

            val a = typeUtils.findReturnType(it.type)

            if (definition.name == "Query") {
                val kotlinFile = createQueryContext(it, definition.name)
                CodeGenResult(kotlinQueryTypes = listOf(kotlinFile)).merge(rootProjection)
            } else {
                CodeGenResult().merge(rootProjection)
            }
        }.fold(CodeGenResult()) { total, current -> total.merge(current) }
    }

    private fun createQueryContext(it: FieldDefinition, operation: String): FileSpec {
        val className = "QueryProjection"
        val kotlinType = TypeSpec.classBuilder(className)
            .addAnnotation(DgsDslContext::class)
            .superclass(BaseProjectionNode::class)

        val subProjectionType = it.type.findTypeDefinition(document)

        if (subProjectionType != null) {
            val subProjectionTypeClassName =
                ClassName(
                    getPackageName(),
                    ReservedKeywordSanitizer.sanitize("${subProjectionType.name.capitalized()}Projection")
                )

            kotlinType.addFunction(createSubProjectionFunction(className, it.name, subProjectionTypeClassName))
        }

        val typeSpec = kotlinType.build()
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
        val className = "${prefix}Projection"
        val kotlinType = TypeSpec.classBuilder(className)
            .addAnnotation(DgsDslContext::class)
            .superclass(BaseProjectionNode::class)

        if (generatedClasses.contains(className)) return CodeGenResult() else generatedClasses.add(className)

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

                kotlinType.addFunction(createSubProjectionFunction(className, fieldDef.name, subProjectionType))

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

    private fun createSubProjectionFunction(
        className: String,
        fieldName: String,
        subProjectionType: ClassName
    ) = FunSpec.builder(fieldName)
        .addParameter(
            ParameterSpec
                .builder("initBlock", LambdaTypeName.get(receiver = subProjectionType, returnType = UNIT))
                .build()
        )
            // this@QueryProjection
        .returns(subProjectionType)
        .addCode("return %T().apply {\n", subProjectionType)
        .addStatement("this@%T.fields[%S] = this", ClassName("", className), fieldName)
        .addStatement("initBlock()")
        .addCode("}")
        .build()

    private fun getPackageName(): String {
        return config.packageNameClient
    }
}

@DslMarker
annotation class DgsDslContext
