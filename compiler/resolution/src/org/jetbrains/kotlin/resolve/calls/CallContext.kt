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

import org.jetbrains.kotlin.resolve.calls.components.ArgumentsToParametersMapper
import org.jetbrains.kotlin.resolve.calls.components.CallableReferenceResolver
import org.jetbrains.kotlin.resolve.calls.components.TypeArgumentsToParametersMapper
import org.jetbrains.kotlin.resolve.calls.inference.components.ConstraintInjector
import org.jetbrains.kotlin.resolve.calls.inference.components.ResultTypeResolver
import org.jetbrains.kotlin.resolve.calls.inference.model.NewConstraintSystemImpl
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.calls.tower.*
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValueWithSmartCastInfo

class CallContextComponents(
        val argumentsToParametersMapper: ArgumentsToParametersMapper,
        val typeArgumentsToParametersMapper: TypeArgumentsToParametersMapper,
        val resultTypeResolver: ResultTypeResolver,
        val callableReferenceResolver: CallableReferenceResolver,
        val constraintInjector: ConstraintInjector
)

class CallContext(
        val c: CallContextComponents,
        val scopeTower: ImplicitScopeTower,
        val astCall: ASTCall,
        val lambdaAnalyzer: LambdaAnalyzer
): CandidateFactory<SimpleResolutionCandidate> {

    // todo: try something else, because current method is ugly and unstable
    private fun createReceiverArgument(
            explicitReceiver: ReceiverCallArgument?,
            fromResolution: ReceiverValueWithSmartCastInfo?
    ): SimpleCallArgument? =
            explicitReceiver as? SimpleCallArgument ?: // qualifier receiver cannot be safe
            fromResolution?.let { ReceiverExpressionArgument(it, isSafeCall = false) } // only explicit receiver can be smart cast

    override fun createCandidate(
            towerCandidate: CandidateWithBoundDispatchReceiver,
            explicitReceiverKind: ExplicitReceiverKind,
            extensionReceiver: ReceiverValueWithSmartCastInfo?
    ): SimpleResolutionCandidate {
        val dispatchArgumentReceiver = createReceiverArgument(astCall.getExplicitDispatchReceiver(explicitReceiverKind),
                                                              towerCandidate.dispatchReceiver)
        val extensionArgumentReceiver = createReceiverArgument(astCall.getExplicitExtensionReceiver(explicitReceiverKind), extensionReceiver)

        return SimpleResolutionCandidate(this, explicitReceiverKind, dispatchArgumentReceiver, extensionArgumentReceiver,
                                         towerCandidate.descriptor, NewConstraintSystemImpl(c), towerCandidate.diagnostics)
    }

    fun replaceCall(newCall: ASTCall) = CallContext(c, scopeTower, newCall, lambdaAnalyzer)
}

enum class ASTCallKind(vararg resolutionPart: ResolutionPart) {
    VARIABLE(
            CheckVisibility,
            NoTypeArguments,
            NoArguments,
            CreteDescriptorWithFreshTypeVariables,
            CheckExplicitReceiverKindConsistency,
            CheckReceivers
    ),
    FUNCTION(
            CheckVisibility,
            MapTypeArguments,
            MapArguments,
            CreteDescriptorWithFreshTypeVariables,
            CheckExplicitReceiverKindConsistency,
            CheckReceivers,
            CheckArguments
    ),
    UNSUPPORTED()
    ;

    val resolutionSequence = resolutionPart.asList()
}



