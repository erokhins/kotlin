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

import org.jetbrains.kotlin.builtins.ReflectionTypes
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.resolve.calls.components.*
import org.jetbrains.kotlin.resolve.calls.inference.components.ConstraintInjector
import org.jetbrains.kotlin.resolve.calls.inference.components.ResultTypeResolver
import org.jetbrains.kotlin.resolve.calls.inference.model.ConstraintStorage
import org.jetbrains.kotlin.resolve.calls.inference.model.NewConstraintSystemImpl
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.calls.tower.*
import org.jetbrains.kotlin.resolve.descriptorUtil.hasDynamicExtensionAnnotation
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValueWithSmartCastInfo
import org.jetbrains.kotlin.types.ErrorUtils
import org.jetbrains.kotlin.types.TypeSubstitutor
import org.jetbrains.kotlin.types.isDynamic
import org.jetbrains.kotlin.utils.addToStdlib.safeAs


class KotlinCallComponents(
        val statelessCallbacks: KotlinResolutionStatelessCallbacks,
        val argumentsToParametersMapper: ArgumentsToParametersMapper,
        val typeArgumentsToParametersMapper: TypeArgumentsToParametersMapper,
        val resultTypeResolver: ResultTypeResolver,
        val constraintInjector: ConstraintInjector,
        val reflectionTypes: ReflectionTypes
)

class SimpleCandidateFactory(
        private val callComponents: KotlinCallComponents,
        private val scopeTower: ImplicitScopeTower,
        private val kotlinCall: KotlinCall
): CandidateFactory<KotlinResolutionCandidate> {
    private val baseSystem: ConstraintStorage

    init {
        fun NewConstraintSystemImpl.addSubsystem(argument: KotlinCallArgument?) {
            when (argument) {
                is SubKotlinCallArgument -> addOtherSystem(argument.subSystem)
                is CallableReferenceKotlinCallArgument -> {
                    addSubsystem(argument.lhsResult.safeAs<LHSResult.Expression>()?.lshCallArgument)
                }
            }
        }

        val baseSystem = NewConstraintSystemImpl(callComponents.constraintInjector, callComponents.resultTypeResolver)
        baseSystem.addSubsystem(kotlinCall.explicitReceiver)
        baseSystem.addSubsystem(kotlinCall.dispatchReceiverForInvokeExtension)
        for (argument in kotlinCall.argumentsInParenthesis) {
            baseSystem.addSubsystem(argument)
        }
        baseSystem.addSubsystem(kotlinCall.externalArgument)

        this.baseSystem = baseSystem.asReadOnlyStorage()
    }

    // todo: try something else, because current method is ugly and unstable
    private fun createReceiverArgument(
            explicitReceiver: ReceiverKotlinCallArgument?,
            fromResolution: ReceiverValueWithSmartCastInfo?
    ): SimpleKotlinCallArgument? =
            explicitReceiver as? SimpleKotlinCallArgument ?: // qualifier receiver cannot be safe
            fromResolution?.let { ReceiverExpressionKotlinCallArgument(it, isSafeCall = false) } // todo smartcast implicit this

    private fun KotlinCall.getExplicitDispatchReceiver(explicitReceiverKind: ExplicitReceiverKind) = when (explicitReceiverKind) {
        ExplicitReceiverKind.DISPATCH_RECEIVER -> explicitReceiver
        ExplicitReceiverKind.BOTH_RECEIVERS -> dispatchReceiverForInvokeExtension
        else -> null
    }

    private fun KotlinCall.getExplicitExtensionReceiver(explicitReceiverKind: ExplicitReceiverKind) = when (explicitReceiverKind) {
        ExplicitReceiverKind.EXTENSION_RECEIVER, ExplicitReceiverKind.BOTH_RECEIVERS -> explicitReceiver
        else -> null
    }

    override fun createCandidate(
            towerCandidate: CandidateWithBoundDispatchReceiver,
            explicitReceiverKind: ExplicitReceiverKind,
            extensionReceiver: ReceiverValueWithSmartCastInfo?
    ): KotlinResolutionCandidate {
        val dispatchArgumentReceiver = createReceiverArgument(kotlinCall.getExplicitDispatchReceiver(explicitReceiverKind),
                                                              towerCandidate.dispatchReceiver)
        val extensionArgumentReceiver = createReceiverArgument(kotlinCall.getExplicitExtensionReceiver(explicitReceiverKind), extensionReceiver)

        if (ErrorUtils.isError(towerCandidate.descriptor)) {
            TODO()
        }

        val resolvedKtCall = MutableResolvedKtCall(kotlinCall, towerCandidate.descriptor, explicitReceiverKind,
                                                   dispatchArgumentReceiver, extensionArgumentReceiver)

        val candidate = KotlinResolutionCandidate(callComponents, scopeTower, baseSystem,resolvedKtCall)

        towerCandidate.diagnostics.forEach(candidate::addDiagnostic)

        if (callComponents.statelessCallbacks.isHiddenInResolution(towerCandidate.descriptor, kotlinCall)) {
            candidate.addDiagnostic(HiddenDescriptor)
        }

        if (extensionReceiver != null) {
            val parameterIsDynamic = towerCandidate.descriptor.extensionReceiverParameter!!.value.type.isDynamic()
            val argumentIsDynamic = extensionReceiver.receiverValue.type.isDynamic()

            if (parameterIsDynamic != argumentIsDynamic ||
                (parameterIsDynamic && !towerCandidate.descriptor.hasDynamicExtensionAnnotation())) {
                candidate.addDiagnostic(HiddenExtensionRelatedToDynamicTypes)
            }
        }

        return candidate
    }
}

enum class KotlinCallKind(vararg resolutionPart: ResolutionPart) {
    VARIABLE(
            CheckVisibility,
            CheckInfixResolutionPart,
            CheckOperatorResolutionPart,
            CheckAbstractSuperCallPart,
            NoTypeArguments,
            NoArguments,
            CreateDescriptorWithFreshTypeVariables,
            CheckExplicitReceiverKindConsistency,
            CheckReceivers
    ),
    FUNCTION(
            CheckInstantiationOfAbstractClass,
            CheckVisibility,
            CheckInfixResolutionPart,
            CheckAbstractSuperCallPart,
            MapTypeArguments,
            MapArguments,
            CreateDescriptorWithFreshTypeVariables,
            CheckExplicitReceiverKindConsistency,
            CheckReceivers,
            CheckArguments
    ),
    UNSUPPORTED();

    val resolutionSequence = resolutionPart.asList()
}

class GivenCandidate(
        val scopeTower: ImplicitScopeTower,
        val descriptor: FunctionDescriptor,
        val dispatchReceiver: ReceiverValueWithSmartCastInfo?,
        val knownTypeParametersResultingSubstitutor: TypeSubstitutor?
)

