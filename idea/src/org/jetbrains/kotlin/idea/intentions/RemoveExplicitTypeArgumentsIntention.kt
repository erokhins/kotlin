/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.resolve.frontendService
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.inspections.IntentionBasedInspection
import org.jetbrains.kotlin.idea.util.getLexicalScope
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTraceContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getDataFlowInfo
import org.jetbrains.kotlin.resolve.calls.CallResolver
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.DelegatingCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.JetType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.checker.JetTypeChecker

public class RemoveExplicitTypeArgumentsInspection : IntentionBasedInspection<JetTypeArgumentList>(RemoveExplicitTypeArgumentsIntention()) {
    override val problemHighlightType: ProblemHighlightType
        get() = ProblemHighlightType.LIKE_UNUSED_SYMBOL
}

public class RemoveExplicitTypeArgumentsIntention : JetSelfTargetingOffsetIndependentIntention<JetTypeArgumentList>(javaClass(), "Remove explicit type arguments") {
    companion object {
        public fun isApplicableTo(element: JetTypeArgumentList, approximateFlexible: Boolean): Boolean {
            val callExpression = element.getParent() as? JetCallExpression ?: return false
            if (callExpression.getTypeArguments().isEmpty()) return false

            val resolutionFacade = callExpression.getResolutionFacade()
            val context = resolutionFacade.analyze(callExpression, BodyResolveMode.PARTIAL)
            val scope = callExpression.getLexicalScope(context)
            val originalCall = callExpression.getResolvedCall(context) ?: return false
            val untypedCall = CallWithoutTypeArgs(originalCall.getCall())

            // todo Check with expected type for other expressions
            // If always use expected type from trace there is a problem with nested calls:
            // the expression type for them can depend on their explicit type arguments (via outer call),
            // therefore we should resolve outer call with erased type arguments for inner call
            val parent = callExpression.getParent()
            val expectedTypeIsExplicitInCode = when (parent) {
                is JetProperty -> parent.getInitializer() == callExpression && parent.getTypeReference() != null
                is JetDeclarationWithBody -> parent.getBodyExpression() == callExpression
                is JetReturnExpression -> true
                else -> false
            }
            val expectedType = if (expectedTypeIsExplicitInCode) {
                context[BindingContext.EXPECTED_EXPRESSION_TYPE, callExpression] ?: TypeUtils.NO_EXPECTED_TYPE
            }
            else {
                TypeUtils.NO_EXPECTED_TYPE
            }
            val dataFlow = context.getDataFlowInfo(callExpression)
            val callResolver = resolutionFacade.frontendService<CallResolver>()
            val resolutionResults = callResolver.resolveFunctionCall(
                    BindingTraceContext(), scope, untypedCall, expectedType, dataFlow, false)
            if (!resolutionResults.isSingleResult()) {
                return false
            }

            val args = originalCall.getTypeArguments()
            val newArgs = resolutionResults.getResultingCall().getTypeArguments()

            fun equalTypes(type1: JetType, type2: JetType): Boolean {
                return if (approximateFlexible) {
                    JetTypeChecker.DEFAULT.equalTypes(type1, type2)
                }
                else {
                    type1 == type2
                }
            }

            return args.size() == newArgs.size() && args.values().zip(newArgs.values()).all { pair -> equalTypes(pair.first, pair.second) }
        }
    }

    override fun isApplicableTo(element: JetTypeArgumentList): Boolean {
        return isApplicableTo(element, approximateFlexible = false)
    }

    private class CallWithoutTypeArgs(call: Call) : DelegatingCall(call) {
        override fun getTypeArguments() = emptyList<JetTypeProjection>()
        override fun getTypeArgumentList() = null
    }

    override fun applyTo(element: JetTypeArgumentList, editor: Editor) {
        element.delete()
    }
}