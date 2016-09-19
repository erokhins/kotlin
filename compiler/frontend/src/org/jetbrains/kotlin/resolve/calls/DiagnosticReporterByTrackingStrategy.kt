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

import org.jetbrains.kotlin.psi.Call
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.calls.context.BasicCallResolutionContext
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValueFactory
import org.jetbrains.kotlin.resolve.calls.smartcasts.SmartCastManager
import org.jetbrains.kotlin.resolve.calls.tasks.TracingStrategy
import org.jetbrains.kotlin.resolve.calls.tower.ExpressionArgumentImpl
import org.jetbrains.kotlin.resolve.calls.tower.PSIASTCall
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver

class DiagnosticReporterByTrackingStrategy(
        val context: BasicCallResolutionContext,
        val trace: BindingTrace,
        val psiAstCall: PSIASTCall
): DiagnosticReporter {
    private val tracingStrategy: TracingStrategy get() = psiAstCall.tracingStrategy
    private val call: Call get() = psiAstCall.psiCall

    override fun onExplicitReceiver(diagnostic: CallDiagnostic) {

    }

    override fun onCallOperationNode(diagnostic: CallDiagnostic) {

    }

    override fun onCall(diagnostic: CallDiagnostic) {

    }

    override fun onTypeArguments(diagnostic: CallDiagnostic) {

    }

    override fun onCallName(diagnostic: CallDiagnostic) {

    }

    override fun onTypeArgument(typeArgument: TypeArgument, diagnostic: CallDiagnostic) {

    }

    override fun onCallReceiver(callReceiver: SimpleCallArgument, diagnostic: CallDiagnostic) {

    }

    override fun onCallArgument(callArgument: CallArgument, diagnostic: CallDiagnostic) {
        when (diagnostic.javaClass) {
            SmartCastDiagnostic::class.java -> reportSmartCast(diagnostic as SmartCastDiagnostic)
        }
    }

    override fun onCallArgumentName(callArgument: CallArgument, diagnostic: CallDiagnostic) {

    }

    override fun onCallArgumentSpread(callArgument: CallArgument, diagnostic: CallDiagnostic) {

    }

    private fun reportSmartCast(smartCastDiagnostic: SmartCastDiagnostic) {
        val expressionArgument = smartCastDiagnostic.expressionArgument
        if (expressionArgument is ExpressionArgumentImpl) {
            expressionArgument.valueArgument
        }
        else if(expressionArgument is ReceiverExpressionArgument) {
            val receiverValue = expressionArgument.receiver.receiverValue
            val dataFlowValue = DataFlowValueFactory.createDataFlowValue(receiverValue, context)
            SmartCastManager.checkAndRecordPossibleCast(
                    dataFlowValue, smartCastDiagnostic.smartCastType, (receiverValue as? ExpressionReceiver)?.expression, context, call,
                    recordExpressionType = true)
        }
    }
}