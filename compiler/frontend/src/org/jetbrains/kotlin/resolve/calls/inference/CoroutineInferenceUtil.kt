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

import org.jetbrains.kotlin.builtins.getReceiverTypeFromFunctionType
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.renderer.DescriptorRendererOptions
import org.jetbrains.kotlin.resolve.calls.ArgumentTypeResolver
import org.jetbrains.kotlin.resolve.calls.ArgumentTypeResolver.getCallableReferenceExpressionIfAny
import org.jetbrains.kotlin.resolve.calls.ArgumentTypeResolver.getFunctionLiteralArgumentIfAny
import org.jetbrains.kotlin.resolve.calls.CallCompleter
import org.jetbrains.kotlin.resolve.calls.callResolverUtil.ResolveArgumentsMode.RESOLVE_FUNCTION_ARGUMENTS
import org.jetbrains.kotlin.resolve.calls.context.BasicCallResolutionContext
import org.jetbrains.kotlin.resolve.calls.context.CallCandidateResolutionContext
import org.jetbrains.kotlin.resolve.calls.context.CallResolutionContext
import org.jetbrains.kotlin.resolve.calls.context.ContextDependency
import org.jetbrains.kotlin.resolve.calls.model.ArgumentMatch
import org.jetbrains.kotlin.resolve.calls.model.MutableResolvedCall
import org.jetbrains.kotlin.resolve.calls.results.OverloadResolutionResultsImpl
import org.jetbrains.kotlin.resolve.calls.tasks.TracingStrategy
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.TypeUtils.NO_EXPECTED_TYPE
import org.jetbrains.kotlin.types.checker.NewKotlinTypeChecker
import org.jetbrains.kotlin.types.checker.TypeCheckerContext
import org.jetbrains.kotlin.types.expressions.ExpressionTypingServices
import org.jetbrains.kotlin.types.expressions.KotlinTypeInfo
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection
import org.jetbrains.kotlin.types.typeUtil.contains

class TypeTemplate(
        val typeVariable: TypeVariable,
        val coroutineInferenceData: CoroutineInferenceData
) : FlexibleType(typeVariable.originalTypeParameter.builtIns.nothingType, typeVariable.originalTypeParameter.builtIns.nullableAnyType) {
    override fun replaceAnnotations(newAnnotations: Annotations) = this

    override fun makeNullableAsSpecified(newNullability: Boolean) = this

    override val delegate: SimpleType
        get() = upperBound

    override fun render(renderer: DescriptorRenderer, options: DescriptorRendererOptions) =
        "~${renderer.renderType(typeVariable.type)}"


    init {
        coroutineInferenceData.registerTypeTemplate(this)
    }
}

class CoroutineInferenceData(val controllerType: TypeConstructor) {
    val csBuilder = ConstraintSystemBuilderImpl()

    fun registerTypeTemplate(template: TypeTemplate) {
        csBuilder.registerTypeVariables(CallHandle.NONE, listOf(template.typeVariable.originalTypeParameter))
    }

    fun addMemberCall(resolvedCall: MutableResolvedCall<*>) {}
    fun addExtensionCall(resolvedCall: MutableResolvedCall<*>) {}

    fun addConstraint(subType: KotlinType, superType: KotlinType) {}
}

class CoroutineInferenceSupport(
        val argumentTypeResolver: ArgumentTypeResolver,
        val expressionTypingServices: ExpressionTypingServices,
        val callCompleter: CallCompleter
) {


    fun analyzeCoroutine(
            functionLiteral: KtFunction,
            valueArgument: ValueArgument,
            valueParameterDescriptor: ValueParameterDescriptor,
            csBuilder: ConstraintSystem.Builder,
            context: CallCandidateResolutionContext<*>,
            lambdaExpectedType: KotlinType
    ) {
        val argumentExpression = valueArgument.getArgumentExpression() ?: return

        val inferenceData = CoroutineInferenceData(lambdaExpectedType.getReceiverTypeFromFunctionType()?.constructor ?: return)

        val constraintSystem = csBuilder.build()
        val newSubstitution = object : DelegatedTypeSubstitution(constraintSystem.currentSubstitutor.substitution) {
            override fun get(key: KotlinType): TypeProjection? {
                val substitutedType = super.get(key)
                if (substitutedType?.type != TypeUtils.DONT_CARE) return substitutedType

                // todo: what about nullable type?
                val typeVariable = constraintSystem.typeVariables.firstOrNull {
                    it.originalTypeParameter.defaultType == key
                } ?: return substitutedType

                return TypeTemplate(typeVariable, inferenceData).asTypeProjection()
            }

            override fun approximateContravariantCapturedTypes() = true
        }

        val expectedType = newSubstitution.buildSubstitutor().substitute(lambdaExpectedType, Variance.IN_VARIANCE)
        val newContext = context.replaceExpectedType(expectedType)
                .replaceDataFlowInfo(context.candidateCall.dataFlowInfoForArguments.getInfo(valueArgument))
                .replaceContextDependency(ContextDependency.INDEPENDENT)
        val type = argumentTypeResolver.getFunctionLiteralTypeInfo(argumentExpression, functionLiteral, newContext, RESOLVE_FUNCTION_ARGUMENTS).type

    }

    fun checkCoroutineCalls(
            context: BasicCallResolutionContext,
            tracingStrategy: TracingStrategy,
            overloadResults: OverloadResolutionResultsImpl<*>
    ) {
        if (!overloadResults.isSingleResult) return

        val resultingCall = overloadResults.resultingCall
        if (!resultingCall.status.possibleTransformToSuccess() ||
            !isCallWithAdditionalCoroutineInference(resultingCall)) return

        forceInferenceForArguments(context) { _: ValueArgument, _: KotlinType -> /* do nothing */ }

        callCompleter.completeCall(context, overloadResults, tracingStrategy)

        forceInferenceForArguments(context) {
            valueArgument: ValueArgument, kotlinType: KotlinType ->
            val argumentMatch = resultingCall.getArgumentMapping(valueArgument) as? ArgumentMatch ?: return@forceInferenceForArguments

            with(NewKotlinTypeChecker) {
                CoroutineTypeCheckerContext().isSubtypeOf(kotlinType.unwrap(), argumentMatch.valueParameter.type.unwrap())
            }
        }
    }

    class CoroutineTypeCheckerContext : TypeCheckerContext(errorTypeEqualsToAnything = true) {
        override fun addSubtypeConstraint(subType: UnwrappedType, superType: UnwrappedType): Boolean? {
            (subType as? TypeTemplate ?: superType as? TypeTemplate)?.coroutineInferenceData?.addConstraint(subType, superType)
            return null
        }
    }

    private fun forceInferenceForArguments(context: CallResolutionContext<*>, callback: (argument: ValueArgument, argumentType: KotlinType) -> Unit) {
        val infoForArguments = context.dataFlowInfoForArguments
        val call = context.call
        val baseContext = context.replaceContextDependency(ContextDependency.INDEPENDENT).replaceExpectedType(NO_EXPECTED_TYPE)

        for (argument in call.valueArguments) {
            val expression = argument.getArgumentExpression() ?: continue
            val typeInfoForCall = getArgumentTypeInfo(expression, baseContext.replaceDataFlowInfo(infoForArguments.getInfo(argument)))
            typeInfoForCall.type?.let { callback(argument, it) }
        }
    }

    private fun getArgumentTypeInfo(
            expression: KtExpression,
            context: CallResolutionContext<*>
    ): KotlinTypeInfo {
        getFunctionLiteralArgumentIfAny(expression, context)?.let {
            return argumentTypeResolver.getFunctionLiteralTypeInfo(expression, it, context, RESOLVE_FUNCTION_ARGUMENTS)
        }

        getCallableReferenceExpressionIfAny(expression, context)?.let {
            return argumentTypeResolver.getCallableReferenceTypeInfo(expression, it, context, RESOLVE_FUNCTION_ARGUMENTS)
        }

        return expressionTypingServices.getTypeInfo(expression, context)
    }

    private fun isCallWithAdditionalCoroutineInference(resolvedCall: MutableResolvedCall<out CallableDescriptor>): Boolean {
        fun isCoroutineReceiver(receiverValue: ReceiverValue?): Boolean {
            if (receiverValue == null) return false
            return receiverValue.type.contains { it is TypeTemplate }
        }
        return isCoroutineReceiver(resolvedCall.dispatchReceiver) || isCoroutineReceiver(resolvedCall.extensionReceiver)
    }
}