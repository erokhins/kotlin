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

package org.jetbrains.kotlin.resolve.calls.tower

import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.psiUtil.lastBlockStatementOrThis
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.calls.BaseResolvedCall
import org.jetbrains.kotlin.resolve.calls.LambdaAnalyzer
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.context.ContextDependency
import org.jetbrains.kotlin.resolve.calls.model.ASTCall
import org.jetbrains.kotlin.resolve.calls.model.LambdaArgument
import org.jetbrains.kotlin.resolve.calls.util.createFunctionType
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.types.expressions.ExpressionTypingServices

class LambdaAnalyzerImpl(
        val expressionTypingServices: ExpressionTypingServices,
        val trace: BindingTrace
): LambdaAnalyzer {

    override fun analyzeAndGetRelatedCalls(
            topLevelCall: ASTCall,
            lambdaArgument: LambdaArgument,
            receiverType: UnwrappedType?,
            parameters: List<UnwrappedType>,
            expectedReturnType: UnwrappedType?
    ): List<BaseResolvedCall<*>> {
        val psiCallArgument = lambdaArgument.psiCallArgument
        val outerCallContext = (psiCallArgument as? LambdaArgumentIml)?.outerCallContext ?:
                               (psiCallArgument as FunctionExpressionImpl).outerCallContext
        val expression: KtExpression = (psiCallArgument as? LambdaArgumentIml)?.ktLambdaExpression ?:
                               (psiCallArgument as FunctionExpressionImpl).ktFunction

        val builtIns = outerCallContext.scope.ownerDescriptor.builtIns
        val expectedType = createFunctionType(builtIns, Annotations.EMPTY, receiverType, parameters,
                           expectedReturnType ?: TypeUtils.NO_EXPECTED_TYPE)

        val actualContext = outerCallContext.replaceBindingTrace(trace).
                replaceContextDependency(ContextDependency.INDEPENDENT).replaceExpectedType(expectedType)


        val type = expressionTypingServices.getTypeInfo(expression, actualContext).type?.unwrap() ?: return emptyList()

        val lastExpression: KtExpression?
        if (psiCallArgument is LambdaArgumentIml) {
            lastExpression = psiCallArgument.ktLambdaExpression.bodyExpression?.statements?.last()
        }
        else {
            lastExpression = (psiCallArgument as FunctionExpressionImpl).ktFunction.bodyExpression?.lastBlockStatementOrThis()
        }

        val deparentesized = KtPsiUtil.deparenthesize(lastExpression)
        val resolvedCall = deparentesized.getResolvedCall(trace.bindingContext) ?: return emptyList()
        val completedCall = if (resolvedCall is NewResolvedCallImpl) {
            resolvedCall.completedCall
        }
        else if (resolvedCall is NewVariableAsFunctionResolvedCallImpl) {
            resolvedCall.completedCall
        }
        else null
        if (completedCall != null) {
            return listOf(BaseResolvedCall.CompletedResolvedCall(completedCall, emptyList()))
        }

        // todo other cases
        return emptyList()
    }
}