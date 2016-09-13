/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.resolve.calls.inference.model

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.resolve.calls.model.ASTCall
import org.jetbrains.kotlin.resolve.calls.model.LambdaArgument
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.types.*


class TypeVariableTypeConstructor(private val builtIns: KotlinBuiltIns, val debugName: String): TypeConstructor {
    override val annotations: Annotations get() = Annotations.EMPTY

    override fun getParameters(): List<TypeParameterDescriptor> = emptyList()
    override fun getSupertypes(): Collection<KotlinType> = emptyList()
    override fun isFinal(): Boolean = false
    override fun isDenotable(): Boolean = false
    override fun getDeclarationDescriptor(): ClassifierDescriptor? = null

    override fun getBuiltIns() = builtIns

    override fun toString() = "TypeVariable($debugName)"
}

sealed class NewTypeVariable(builtIns: KotlinBuiltIns, name: String) {
    val freshTypeConstructor: TypeConstructor = TypeVariableTypeConstructor(builtIns, name)

    val defaultType: SimpleType = KotlinTypeFactory.simpleType(
            Annotations.EMPTY, freshTypeConstructor, arguments = emptyList(),
            nullable = false, memberScope = ErrorUtils.createErrorScope("Type variable", true))

    override fun toString() = freshTypeConstructor.toString()
}

class TypeVariableFromCallableDescriptor(
        val call: ASTCall,
        val originalTypeParameter: TypeParameterDescriptor
) : NewTypeVariable(originalTypeParameter.builtIns, originalTypeParameter.name.identifier)

class LambdaTypeVariable(
        val lambdaArgument: LambdaArgument,
        val kind: Kind,
        builtIns: KotlinBuiltIns
) : NewTypeVariable(builtIns, createDebugName(lambdaArgument, kind)) {
    enum class Kind {
        RECEIVER,
        PARAMETER,
        RETURN_TYPE
    }
}

private fun createDebugName(lambdaArgument: LambdaArgument, kind: LambdaTypeVariable.Kind): String {
    val text = lambdaArgument.toString().let { it.substring(0..(Math.min(20, it.lastIndex))) }
    return when (kind) {
        LambdaTypeVariable.Kind.RECEIVER -> "().$text"
        LambdaTypeVariable.Kind.PARAMETER -> "(P) in $text"
        LambdaTypeVariable.Kind.RETURN_TYPE -> "$text -> R"
    }
}