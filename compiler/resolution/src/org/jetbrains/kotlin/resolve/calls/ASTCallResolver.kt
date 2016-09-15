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

import org.jetbrains.kotlin.resolve.calls.context.CheckArgumentTypesMode
import org.jetbrains.kotlin.resolve.calls.model.NewResolutionCandidate
import org.jetbrains.kotlin.resolve.calls.tower.CandidateFactoryProviderForInvoke
import org.jetbrains.kotlin.resolve.calls.tower.TowerResolver
import org.jetbrains.kotlin.resolve.calls.tower.createFunctionProcessor
import org.jetbrains.kotlin.resolve.calls.tower.createVariableAndObjectProcessor
import org.jetbrains.kotlin.types.UnwrappedType
import java.lang.UnsupportedOperationException


class ASTCallResolver(
        private val towerResolver: TowerResolver,
        private val astCallCompleter: ASTCallCompleter,
        private val overloadingConflictResolver: org.jetbrains.kotlin.resolve.calls.components.NewOverloadingConflictResolver
) {

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
}

