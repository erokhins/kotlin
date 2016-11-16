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

import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.renderer.DescriptorRendererOptions
import org.jetbrains.kotlin.resolve.calls.ArgumentTypeResolver
import org.jetbrains.kotlin.resolve.calls.callResolverUtil.ResolveArgumentsMode
import org.jetbrains.kotlin.resolve.calls.context.CallCandidateResolutionContext
import org.jetbrains.kotlin.resolve.calls.context.ContextDependency
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection

class TypeTemplate(
        val typeVariable: TypeVariable
) : FlexibleType(typeVariable.originalTypeParameter.builtIns.nothingType, typeVariable.originalTypeParameter.builtIns.nullableAnyType) {
    override fun replaceAnnotations(newAnnotations: Annotations) = this

    override fun makeNullableAsSpecified(newNullability: Boolean) = this

    override val delegate: SimpleType
        get() = upperBound

    override fun render(renderer: DescriptorRenderer, options: DescriptorRendererOptions) =
        "~${renderer.renderType(typeVariable.type)}"
}

class CoroutineInferenceSupport(val argumentTypeResolver: ArgumentTypeResolver) {


    fun analyzeCoroutine(
            functionLiteral: KtFunction,
            valueArgument: ValueArgument,
            valueParameterDescriptor: ValueParameterDescriptor,
            csBuilder: ConstraintSystem.Builder,
            context: CallCandidateResolutionContext<*>,
            lambdaExpectedType: KotlinType
    ) {
        val argumentExpression = valueArgument.getArgumentExpression() ?: return

        val constraintSystem = csBuilder.build()
        val newSubstitution = object : DelegatedTypeSubstitution(constraintSystem.currentSubstitutor.substitution) {
            override fun get(key: KotlinType): TypeProjection? {
                val substitutedType = super.get(key)
                if (substitutedType != TypeUtils.DONT_CARE) return substitutedType

                // todo: what about nullable key?
                val typeVariable = constraintSystem.typeVariables.firstOrNull {
                    it.type == key
                } ?: return substitutedType

                return TypeTemplate(typeVariable).asTypeProjection()
            }

            override fun approximateContravariantCapturedTypes() = true
        }

        val expectedType = newSubstitution.buildSubstitutor().substitute(lambdaExpectedType, Variance.IN_VARIANCE)
        val newContext = context.replaceExpectedType(expectedType)
                .replaceDataFlowInfo(context.candidateCall.dataFlowInfoForArguments.getInfo(valueArgument))
                .replaceContextDependency(ContextDependency.INDEPENDENT)
        val type = argumentTypeResolver.getFunctionLiteralTypeInfo(argumentExpression, functionLiteral, newContext, ResolveArgumentsMode.RESOLVE_FUNCTION_ARGUMENTS).type

    }
}