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

package org.jetbrains.kotlin.resolve.calls

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.resolve.calls.context.CheckArgumentTypesMode
import org.jetbrains.kotlin.resolve.calls.inference.SimpleConstraintSystemImpl
import org.jetbrains.kotlin.resolve.calls.model.CallArgument
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCallArgument
import org.jetbrains.kotlin.resolve.calls.results.FlatSignature
import org.jetbrains.kotlin.resolve.calls.results.FlatSignature.Companion.argumentValueType
import org.jetbrains.kotlin.resolve.calls.results.FlatSignature.Companion.extensionReceiverTypeOrEmpty
import org.jetbrains.kotlin.resolve.calls.results.OverloadingConflictResolver
import org.jetbrains.kotlin.resolve.calls.results.TypeSpecificityComparator
import org.jetbrains.kotlin.resolve.calls.tower.CandidateFactoryProviderForInvoke
import org.jetbrains.kotlin.resolve.calls.tower.TowerResolver
import org.jetbrains.kotlin.resolve.calls.tower.createFunctionProcessor
import org.jetbrains.kotlin.resolve.calls.tower.createVariableAndObjectProcessor
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.utils.singletonOrEmptyList
import java.lang.UnsupportedOperationException
import java.util.*


class ASTCallResolver(
        private val towerResolver: TowerResolver,
        private val astCallCompleter: ASTCallCompleter,
        builtIns: KotlinBuiltIns,
        specificityComparator: TypeSpecificityComparator,
        isDescriptorFromSourcePredicate: IsDescriptorFromSourcePredicate
) {
    private val overloadingConflictResolver = createOverloadingConflictResolver(builtIns, specificityComparator, isDescriptorFromSourcePredicate)

    fun resolveCall(
            callContext: CallContext,
            expectedType: UnwrappedType?,
            factoryProviderForInvoke: CandidateFactoryProviderForInvoke<NewResolutionCandidate>
    ): Collection<BaseResolvedCall> {
        val call = callContext.astCall
        val scopeTower = callContext.scopeTower

        val processor = when(callContext.astCall.callKind) {
            ASTCallKind.VARIABLE -> {
                createVariableAndObjectProcessor(scopeTower, call.name, callContext, call.explicitReceiver?.receiver)
            }
            ASTCallKind.FUNCTION -> {
                createFunctionProcessor(scopeTower, call.name, callContext, factoryProviderForInvoke, call.explicitReceiver?.receiver)
            }
            ASTCallKind.UNSUPPORTED -> throw UnsupportedOperationException()
        }

        val candidates = towerResolver.runResolve(scopeTower, processor, useOrder = call.callKind != ASTCallKind.UNSUPPORTED)
        val maximallySpecificCandidates = overloadingConflictResolver.chooseMaximallySpecificCandidates(candidates,
                                                                   CheckArgumentTypesMode.CHECK_VALUE_ARGUMENTS,
                                                                   discriminateGenerics = true, // todo
                                                                   isDebuggerContext = scopeTower.isDebuggerContext)
        val singleResult = maximallySpecificCandidates.singleOrNull()?.let {
            astCallCompleter.completeCallIfNecessary(it, expectedType, callContext.lambdaAnalyzer)
        }
        if (singleResult != null) {
            return listOf(singleResult)
        }

        return maximallySpecificCandidates.map {
            astCallCompleter.transformWhenAmbiguity(it)
        }
    }

    companion object {
        private fun createOverloadingConflictResolver(
                builtIns: KotlinBuiltIns,
                specificityComparator: TypeSpecificityComparator,
                isDescriptorFromSourcePredicate: IsDescriptorFromSourcePredicate
        ) = OverloadingConflictResolver<NewResolutionCandidate>(builtIns, specificityComparator, {
            if (it is VariableAsFunctionResolutionCandidate) {
                it.invokeCandidate.descriptorWithFreshTypes
            }
            else {
                (it as SimpleResolutionCandidate).descriptorWithFreshTypes
            }
        }, ::SimpleConstraintSystemImpl, Companion::createFlatSignature, { (it as? VariableAsFunctionResolutionCandidate)?.resolvedVariable }, isDescriptorFromSourcePredicate)

        private fun createFlatSignature(candidate: NewResolutionCandidate): FlatSignature<NewResolutionCandidate> {
            val simpleCandidate = (candidate as? VariableAsFunctionResolutionCandidate)?.invokeCandidate ?: (candidate as SimpleResolutionCandidate)

            val originalDescriptor = simpleCandidate.descriptorWithFreshTypes.original
            val originalValueParameters = originalDescriptor.valueParameters

            var numDefaults = 0
            val valueArgumentToParameterType = HashMap<CallArgument, KotlinType>()
            for ((valueParameter, resolvedValueArgument) in simpleCandidate.argumentMappingByOriginal) {
                if (resolvedValueArgument is ResolvedCallArgument.DefaultArgument) {
                    numDefaults++
                }
                else {
                    val originalValueParameter = originalValueParameters[valueParameter.index]
                    val parameterType = originalValueParameter.argumentValueType
                    for (valueArgument in resolvedValueArgument.arguments) {
                        valueArgumentToParameterType[valueArgument] = parameterType
                    }
                }
            }

            return FlatSignature(candidate,
                                 originalDescriptor.typeParameters,
                                 valueParameterTypes = originalDescriptor.extensionReceiverTypeOrEmpty() +
                                                       simpleCandidate.astCall.argumentsInParenthesis.map { valueArgumentToParameterType[it] } +
                                                       simpleCandidate.astCall.externalArgument?.let { valueArgumentToParameterType[it] }.singletonOrEmptyList(),
                                 hasExtensionReceiver = originalDescriptor.extensionReceiverParameter != null,
                                 hasVarargs = originalDescriptor.valueParameters.any { it.varargElementType != null },
                                 numDefaults = numDefaults)

        }
    }
}

