/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.resolve.calls.model

import org.jetbrains.kotlin.builtins.getReceiverTypeFromFunctionType
import org.jetbrains.kotlin.builtins.getReturnTypeFromFunctionType
import org.jetbrains.kotlin.builtins.getValueParameterTypesFromFunctionType
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.resolve.calls.components.CallableReferenceCandidate
import org.jetbrains.kotlin.resolve.calls.components.TypeArgumentsToParametersMapper
import org.jetbrains.kotlin.resolve.calls.components.getFunctionTypeFromCallableReferenceExpectedType
import org.jetbrains.kotlin.resolve.calls.inference.components.FreshVariableNewTypeSubstitutor
import org.jetbrains.kotlin.resolve.calls.inference.model.ConstraintStorage
import org.jetbrains.kotlin.resolve.calls.inference.model.TypeVariableForLambdaReturnType
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.types.UnwrappedType

/**
 * Call, Callable reference, lambda & function expression, collection literal.
 * In future we should add literals here, because they have similar lifecycle.
 *
 * Expression with type is also primitive. This is done for simplification. todo
 */
interface KtPrimitive

enum class ResolvedKtPrimitiveState {
    INITIAL,
    ADDITIONAL_ANALYSIS_PERFORMED // arguments analyzed, lambda body resolved, ::foo <- foo resolved
}

sealed class ResolvedKtPrimitive {
    abstract val ktPrimitive: KtPrimitive? // some additional elements can have no ktPrimitive

    var state: ResolvedKtPrimitiveState = ResolvedKtPrimitiveState.INITIAL
        private set

    lateinit var subKtPrimitives: List<ResolvedKtPrimitive>
        private set
    lateinit var diagnostics: Collection<KotlinCallDiagnostic>
        private set

    fun setAnalyzedResults(subKtPrimitives: List<ResolvedKtPrimitive>, diagnostics: Collection<KotlinCallDiagnostic>) {
        assert(state == ResolvedKtPrimitiveState.INITIAL) {
            "Unsupported state: $state for $ktPrimitive"
        }

        state = ResolvedKtPrimitiveState.ADDITIONAL_ANALYSIS_PERFORMED
        this.subKtPrimitives = subKtPrimitives
        this.diagnostics = diagnostics
    }
}

abstract class ResolvedKtCall : ResolvedKtPrimitive() {
    abstract override val ktPrimitive: KotlinCall
    abstract val candidateDescriptor: CallableDescriptor
    abstract val explicitReceiverKind: ExplicitReceiverKind
    abstract val dispatchReceiverArgument: SimpleKotlinCallArgument?
    abstract val extensionReceiverArgument: SimpleKotlinCallArgument?
    abstract val typeArgumentMappingByOriginal: TypeArgumentsToParametersMapper.TypeArgumentsMapping
    abstract val argumentMappingByOriginal: Map<ValueParameterDescriptor, ResolvedCallArgument>
    abstract val substitutor: FreshVariableNewTypeSubstitutor
}



class ResolvedKtExpression(override val ktPrimitive: ExpressionKotlinCallArgument) : ResolvedKtPrimitive() {
    init {
        setAnalyzedResults(listOf(), listOf())
    }
}
sealed class PostponedResolveKtPrimitive : ResolvedKtPrimitive() {
    val analyzed get() = state == ResolvedKtPrimitiveState.ADDITIONAL_ANALYSIS_PERFORMED

    abstract val inputTypes: Collection<UnwrappedType>
    abstract val outputType: UnwrappedType?
}

class ResolvedKtLambda(
        override val ktPrimitive: LambdaKotlinCallArgument,
        val isSuspend: Boolean,
        val receiver: UnwrappedType?,
        val parameters: List<UnwrappedType>,
        val returnType: UnwrappedType,
        val typeVariableForLambdaReturnType: TypeVariableForLambdaReturnType?
) : PostponedResolveKtPrimitive() {
    override val inputTypes: Collection<UnwrappedType> get() = receiver?.let { parameters + it } ?: parameters
    override val outputType: UnwrappedType get() = returnType

}

class ResolvedKtCallableReference(
        override val ktPrimitive: CallableReferenceKotlinCallArgument,
        val expectedType: UnwrappedType?
) : PostponedResolveKtPrimitive() {
    var candidate: CallableReferenceCandidate? = null
        private set

    fun setAnalyzedResults(
            candidate: CallableReferenceCandidate?,
            subKtPrimitives: List<ResolvedKtPrimitive>,
            diagnostics: Collection<KotlinCallDiagnostic>
    ) {
        this.candidate = candidate
        setAnalyzedResults(subKtPrimitives, diagnostics)
    }

    override val inputTypes: Collection<UnwrappedType>
        get() {
            val functionType = getFunctionTypeFromCallableReferenceExpectedType(expectedType) ?: return emptyList()
            val parameters = functionType.getValueParameterTypesFromFunctionType().map { it.type.unwrap() }
            val receiver = functionType.getReceiverTypeFromFunctionType()?.unwrap()
            return receiver?.let { parameters + it } ?: parameters
        }

    override val outputType: UnwrappedType?
        get() {
            val functionType = getFunctionTypeFromCallableReferenceExpectedType(expectedType) ?: return null
            return functionType.getReturnTypeFromFunctionType().unwrap()
        }

}

class ResolvedKtCollectionLiteral(
        override val ktPrimitive: CollectionLiteralKotlinCallArgument,
        val expectedType: UnwrappedType?
) : ResolvedKtPrimitive() {
    init {
        setAnalyzedResults(listOf(), listOf())
    }
}


class CallResolutionResult(
        val type: Type,
        val resultCall: ResolvedKtCall?,
        diagnostics: List<KotlinCallDiagnostic>,
        val constraintSystem: ConstraintStorage
) : ResolvedKtPrimitive() {
    override val ktPrimitive: KtPrimitive? get() = null

    enum class Type {
        COMPLETED, // resultSubstitutor possible create use constraintSystem
        PARTIAL,
        ERROR // if resultCall == null it means that there is errors NoneCandidates or ManyCandidates
    }

    init {
        setAnalyzedResults(listOfNotNull(resultCall), diagnostics)
    }
}