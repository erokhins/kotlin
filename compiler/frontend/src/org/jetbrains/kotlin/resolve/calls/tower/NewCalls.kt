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

import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.Call
import org.jetbrains.kotlin.resolve.calls.ASTCallKind
import org.jetbrains.kotlin.resolve.calls.CallTransformer
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.calls.tasks.TracingStrategy
import org.jetbrains.kotlin.resolve.calls.tasks.TracingStrategyForInvoke
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver
import org.jetbrains.kotlin.util.OperatorNameConventions

val USE_NEW_TYPE_INFERENCE = true

val ASTCall.psiAstCall: PSIASTCall get() {
    assert(this is PSIASTCall) {
        "Incorrect ASTCAll: $this. Java class: ${javaClass.canonicalName}"
    }
    return this as PSIASTCall
}

abstract class PSIASTCall : ASTCall {
    abstract val psiCall: Call
    abstract val startingDataFlowInfo: DataFlowInfo
    abstract val resultDataFlowInfo: DataFlowInfo
    abstract val tracingStrategy: TracingStrategy

    override fun toString() = "$psiCall"
}

class ASTCallImpl(
        override val callKind: ASTCallKind,
        override val psiCall: Call,
        override val tracingStrategy: TracingStrategy,
        override val explicitReceiver: ReceiverCallArgument?,
        override val name: Name,
        override val typeArguments: List<TypeArgument>,
        override val argumentsInParenthesis: List<CallArgument>,
        override val externalArgument: CallArgument?,
        override val startingDataFlowInfo: DataFlowInfo,
        override val resultDataFlowInfo: DataFlowInfo
) : PSIASTCall()

class CallForVariable(
        val baseCall: ASTCallImpl,
        override val explicitReceiver: ReceiverCallArgument?,
        override val name: Name
) : PSIASTCall() {
    override val callKind: ASTCallKind get() = ASTCallKind.VARIABLE
    override val typeArguments: List<TypeArgument> get() = emptyList()
    override val argumentsInParenthesis: List<CallArgument> get() = emptyList()
    override val externalArgument: CallArgument? get() = null

    override val startingDataFlowInfo: DataFlowInfo get() = baseCall.startingDataFlowInfo
    override val resultDataFlowInfo: DataFlowInfo get() = baseCall.startingDataFlowInfo

    override val tracingStrategy: TracingStrategy get() = baseCall.tracingStrategy
    override val psiCall: Call

    init {
        psiCall = CallTransformer.stripCallArguments(baseCall.psiCall).let {
            if (explicitReceiver == null) CallTransformer.stripReceiver(it) else it
        }
    }
}

class CallForInvoke(
        val baseCall: ASTCallImpl,
        override val explicitReceiver: SimpleCallArgument,
        override val dispatchReceiverForInvokeExtension: SimpleCallArgument?
) : PSIASTCall() {
    override val callKind: ASTCallKind get() = ASTCallKind.FUNCTION
    override val name: Name get() = OperatorNameConventions.INVOKE
    override val typeArguments: List<TypeArgument> get() = baseCall.typeArguments
    override val argumentsInParenthesis: List<CallArgument> get() = baseCall.argumentsInParenthesis
    override val externalArgument: CallArgument? get() = baseCall.externalArgument

    override val startingDataFlowInfo: DataFlowInfo get() = baseCall.startingDataFlowInfo
    override val resultDataFlowInfo: DataFlowInfo get() = baseCall.resultDataFlowInfo
    override val psiCall: Call
    override val tracingStrategy: TracingStrategy

    init {
        val variableReceiver = dispatchReceiverForInvokeExtension ?: explicitReceiver
        val explicitExtensionReceiver = if (dispatchReceiverForInvokeExtension == null) null else explicitReceiver
        val calleeExpression = baseCall.psiCall.calleeExpression!!

        psiCall = CallTransformer.CallForImplicitInvoke(
                explicitExtensionReceiver?.receiver?.receiverValue,
                variableReceiver.receiver.receiverValue as ExpressionReceiver, baseCall.psiCall, true)
        tracingStrategy = TracingStrategyForInvoke(calleeExpression, psiCall, variableReceiver.receiver.receiverValue.type)

    }
}
