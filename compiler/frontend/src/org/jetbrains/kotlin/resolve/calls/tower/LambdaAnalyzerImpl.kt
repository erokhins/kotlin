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

import org.jetbrains.kotlin.builtins.getReturnTypeFromFunctionType
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.psiUtil.lastBlockStatementOrThis
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.calls.LambdaAnalyzer
import org.jetbrains.kotlin.resolve.calls.context.ContextDependency
import org.jetbrains.kotlin.resolve.calls.context.ResolutionContext
import org.jetbrains.kotlin.resolve.calls.model.ASTCall
import org.jetbrains.kotlin.resolve.calls.model.CallArgument
import org.jetbrains.kotlin.resolve.calls.model.LambdaArgument
import org.jetbrains.kotlin.resolve.calls.util.CallMaker
import org.jetbrains.kotlin.resolve.calls.util.createFunctionType
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.types.expressions.ExpressionTypingServices
import org.jetbrains.kotlin.types.expressions.KotlinTypeInfo
import org.jetbrains.kotlin.utils.SmartList

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
    ): List<CallArgument> {
        val psiCallArgument = lambdaArgument.psiCallArgument
        val outerCallContext = (psiCallArgument as? LambdaArgumentIml)?.outerCallContext ?:
                               (psiCallArgument as FunctionExpressionImpl).outerCallContext
        val expression: KtExpression = (psiCallArgument as? LambdaArgumentIml)?.ktLambdaExpression ?:
                               (psiCallArgument as FunctionExpressionImpl).ktFunction

        val builtIns = outerCallContext.scope.ownerDescriptor.builtIns
        val expectedType = createFunctionType(builtIns, Annotations.EMPTY, receiverType, parameters,
                           null, expectedReturnType ?: TypeUtils.NO_EXPECTED_TYPE)

        val lastExpression = getLastExpression(lambdaArgument)
        val resultList = SmartList<CallArgument>()
        val callbacks = object : ResolutionCallback {

            fun isMyReturnExpression(
                    context: ResolutionContext<*>,
                    returnExpression: KtReturnExpression
            ): Boolean {
                // todo support FunctionExpression
                return (psiCallArgument is LambdaArgumentIml) &&
                       returnExpression.getTargetLabel()?.let { context.trace.get(BindingContext.LABEL_TARGET, it) } ==
                               psiCallArgument.ktLambdaExpression.functionLiteral
            }

            override fun getContextDependencyForReturnExpression(
                    context: ResolutionContext<*>,
                    returnExpression: KtReturnExpression
            ) = if (isMyReturnExpression(context, returnExpression)) ContextDependency.DEPENDENT else ContextDependency.INDEPENDENT

            override fun returnStatement(
                    context: ResolutionContext<*>,
                    returnExpression: KtReturnExpression,
                    typeInfoForReturnedExpression: KotlinTypeInfo
            ) {
                // todo support empty expression(add Unit as SimpleArgument)
                val returnedExpression = returnExpression.returnedExpression ?: return
                if (!isMyReturnExpression(context, returnExpression)) return
                resultList.add(createSimplePSICallArgument(context, CallMaker.makeExternalValueArgument(returnedExpression), typeInfoForReturnedExpression))
            }

            override fun lastStatement(context: ResolutionContext<*>, expression: KtExpression, typeInfo: KotlinTypeInfo) {
                if (expression != lastExpression) return
                resultList.add(createSimplePSICallArgument(context, CallMaker.makeExternalValueArgument(expression), typeInfo))
            }
        }

        val actualContext = outerCallContext.replaceBindingTrace(trace).
                replaceContextDependency(ContextDependency.DEPENDENT).replaceExpectedType(expectedType).replaceResolutionCallback(callbacks)

        expressionTypingServices.getTypeInfo(expression, actualContext)
        return resultList
    }

    private fun getLastExpression(lambdaArgument: LambdaArgument): KtExpression? {
        val psiCallArgument = lambdaArgument.psiCallArgument
        return if (psiCallArgument is LambdaArgumentIml) {
            psiCallArgument.ktLambdaExpression.bodyExpression?.statements?.lastOrNull()
        }
        else {
            (psiCallArgument as FunctionExpressionImpl).ktFunction.bodyExpression?.lastBlockStatementOrThis()
        }
    }
}