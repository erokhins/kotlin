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

package org.jetbrains.kotlin.resolve.calls.inference

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.resolve.calls.BaseResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.types.*

class TypeVariableTypeConstructor(private val builtIns: KotlinBuiltIns, val debugName: String): TypeConstructor {
    override val annotations: Annotations get() = Annotations.EMPTY

    override fun getParameters(): List<TypeParameterDescriptor> = emptyList()
    override fun getSupertypes(): Collection<KotlinType> = emptyList()
    override fun isFinal(): Boolean = true
    override fun isDenotable(): Boolean = false
    override fun getDeclarationDescriptor(): ClassifierDescriptor? = null

    override fun getBuiltIns() = builtIns

    override fun toString() = "TypeVariable($debugName)"
}

// todo add name
sealed class NewTypeVariable(builtIns: KotlinBuiltIns, name: String) {
    val freshTypeConstructor: TypeConstructor = TypeVariableTypeConstructor(builtIns, name)
    val defaultType: SimpleType = KotlinTypeFactory.simpleType(Annotations.EMPTY, freshTypeConstructor, emptyList(),
                                                               nullable = false, memberScope = ErrorUtils.createErrorScope("Type variable", true))

    override fun toString() = freshTypeConstructor.toString()
}

class SimpleNewTypeVariable(
        val call: ASTCall,
        val originalTypeParameter: TypeParameterDescriptor
) : NewTypeVariable(originalTypeParameter.builtIns, originalTypeParameter.name.identifier)

class LambdaNewTypeVariable(
        val outerCall: ASTCall,
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

private fun createDebugName(lambdaArgument: LambdaArgument, kind: LambdaNewTypeVariable.Kind): String {
    val text = lambdaArgument.toString().let { it.substring(0..(Math.min(20, it.lastIndex))) }
    return when (kind) {
        LambdaNewTypeVariable.Kind.RECEIVER -> "().$text"
        LambdaNewTypeVariable.Kind.PARAMETER -> "(P) in $text"
        LambdaNewTypeVariable.Kind.RETURN_TYPE -> "$text -> R"
    }
}

interface ReadOnlyConstraintSystem {
    val allTypeVariables: Map<TypeConstructor, NewTypeVariable>
    val notFixedTypeVariables: Map<TypeConstructor, VariableWithConstraints>
    val initialConstraints: List<InitialConstraint>
    val allowedTypeDepth: Int
    val errors: List<CallDiagnostic>
    val fixedTypeVariables: Map<TypeConstructor, UnwrappedType>
    val lambdaArguments: List<ResolvedLambdaArgument>
    val innerCalls: List<BaseResolvedCall.OnlyResolvedCall>

    object EmptyConstraintSystem : ReadOnlyConstraintSystem {
        override val allTypeVariables: Map<TypeConstructor, NewTypeVariable> get() = emptyMap()
        override val notFixedTypeVariables: Map<TypeConstructor, VariableWithConstraints> get() = emptyMap()
        override val initialConstraints: List<InitialConstraint> get() = emptyList()
        override val allowedTypeDepth: Int get() = 1
        override val errors: List<CallDiagnostic> get() = emptyList()
        override val fixedTypeVariables: Map<TypeConstructor, UnwrappedType> get() = emptyMap()
        override val lambdaArguments: List<ResolvedLambdaArgument> get() = emptyList()
        override val innerCalls: List<BaseResolvedCall.OnlyResolvedCall> get() = emptyList()
    }
}

interface ConstraintSystemBuilder {
    val hasContradiction: Boolean
    val typeVariables: Map<TypeConstructor, VariableWithConstraints>

    // If hasContradiction then this list should contain some diagnostic about problem
    val diagnostics: List<CallDiagnostic>

    fun registerVariable(variable: NewTypeVariable)

    fun addSubtypeConstraint(lowerType: UnwrappedType, upperType: UnwrappedType, position: ConstraintPosition)
    fun addEqualityConstraint(a: UnwrappedType, b: UnwrappedType, position: ConstraintPosition)

    fun addInnerCall(innerCall: BaseResolvedCall.OnlyResolvedCall)
    fun addLambdaArgument(resolvedLambdaArgument: ResolvedLambdaArgument)

    fun addIfIsCompatibleSubtypeConstraint(lowerType: UnwrappedType, upperType: UnwrappedType, position: ConstraintPosition): Boolean




    /**
     * This function removes variables for which we know exact type.
     * @return substitutor from typeVariable to result
     */
    fun simplify(): TypeSubstitutor

    fun build(): ReadOnlyConstraintSystem // return immutable copy of constraint system

    fun startCompletion(): ConstraintStorage
}

sealed class ConstraintPosition

class ExplicitTypeParameterConstraintPosition(val typeArgument: SimpleTypeArgument) : ConstraintPosition()
class ExpectedTypeConstraintPosition(val topLevelCall: ASTCall) : ConstraintPosition()
class DeclaredUpperBoundConstraintPosition(val typeParameterDescriptor: TypeParameterDescriptor) : ConstraintPosition()
class ArgumentConstraintPosition(val argument: CallArgument) : ConstraintPosition()
class FixVariableConstraintPosition(val variable: NewTypeVariable) : ConstraintPosition()
class IncorporationConstraintPosition(val from: ConstraintPosition) : ConstraintPosition()

object SimpleConstraintSystemConstraintPosition : ConstraintPosition()