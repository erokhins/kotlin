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

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.resolve.calls.components.TypeArgumentsToParametersMapper
import org.jetbrains.kotlin.resolve.calls.inference.components.FreshVariableNewTypeSubstitutor
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
    abstract val ktPrimitive: KtPrimitive

    val state: ResolvedKtPrimitiveState = ResolvedKtPrimitiveState.INITIAL

    val subKtPrimitives = arrayListOf<ResolvedKtPrimitive>()
    val diagnostics = arrayListOf<KotlinCallDiagnostic>()
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

// if no candidates or >2 candidates or candidate constraint system contains errors
class ErrorResolvedKtCall(
        override val ktPrimitive: KotlinCall,
        val candidates: Collection<ResolvedKtCall>
) : ResolvedKtPrimitive()

class ResolvedKtCallableReference (
        override val ktPrimitive: CallableReferenceKotlinCallArgument,
        val expectedType: UnwrappedType?
) : ResolvedKtPrimitive() {
}

