/*
 * Copyright 2010-2013 JetBrains s.r.o.
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

package org.jetbrains.k2js.translate.reference

import com.google.dart.compiler.backend.js.ast.JsExpression
import com.google.dart.compiler.backend.js.ast.JsNameRef
import com.google.dart.compiler.backend.js.ast.JsInvocation
import java.util.Collections
import org.jetbrains.jet.parsing.JetParsingTestGenerated.Examples.Collections
import java.util.ArrayList
import org.jetbrains.k2js.translate.context.Namer
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor
import org.jetbrains.jet.lang.descriptors.CallableDescriptor
import org.jetbrains.jet.lang.resolve.calls.tasks.ExplicitReceiverKind

public fun addReceiverToArgs(receiver: JsExpression, arguments: List<JsExpression>) : List<JsExpression> {
    if (arguments.isEmpty())
        return Collections.singletonList(receiver)

    val argumentList = ArrayList<JsExpression>(1 + arguments.size())
    argumentList.add(receiver)
    argumentList.addAll(arguments)
    return argumentList
}


// call may be native and|or with spreadOperator
class DefaultCallCase(callInfo: FunctionCallInfo): FunctionCallCase(callInfo) { //TODO: check spreadOperator

    override fun FunctionCallInfo.bothReceivers(): JsExpression { // TODO: think about crazy case: spreadOperator + native
        val functionRef = JsNameRef(functionName, thisObject!!)
        return JsInvocation(functionRef, addReceiverToArgs(receiverObject!!, argumentsInfo.getTranslateArguments()))
    }

    override fun FunctionCallInfo.thisObject(): JsExpression {
        if (isNative() && hasSpreadOperator()) {
            val cachedReceiver = argumentsInfo.getCachedReceiver()!!
            val functionCallRef = Namer.getFunctionCallRef(JsNameRef(functionName, cachedReceiver.assignmentExpression()))
            return JsInvocation(functionCallRef, argumentsInfo.getTranslateArguments())
        }
        val functionRef = JsNameRef(functionName, thisObject!!)
        return JsInvocation(functionRef, argumentsInfo.getTranslateArguments())
    }

    // TODO: refactor after fix ArgumentsInfo - duplicate code
    override fun FunctionCallInfo.receiverArgument(): JsExpression {
        val qualifierForFunction = context.getQualifierForDescriptor(callableDescriptor)!!
        if (isNative() && hasSpreadOperator()) {
            val functionCallRef = Namer.getFunctionCallRef(JsNameRef(functionName, qualifierForFunction))
            return JsInvocation(functionCallRef, argumentsInfo.getTranslateArguments())
        }
        val functionCall = Namer.getFunctionCallRef(JsNameRef(functionName, qualifierForFunction))
        return JsInvocation(functionCall, addReceiverToArgs(receiverObject!!, argumentsInfo.getTranslateArguments()))
    }
    override fun FunctionCallInfo.noReceivers(): JsExpression {
        val qualifierForFunction = context.getQualifierForDescriptor(callableDescriptor)!!
        if (isNative() && hasSpreadOperator()) {
            val functionCallRef = Namer.getFunctionCallRef(JsNameRef(functionName, qualifierForFunction))
            return JsInvocation(functionCallRef, argumentsInfo.getTranslateArguments())
        }
        val functionCall = JsNameRef(functionName, qualifierForFunction)
        return JsInvocation(functionCall, argumentsInfo.getTranslateArguments())
    }
}


class DelegateFunctionIntrinsic(callInfo: FunctionCallInfo) : FunctionCallCase(callInfo), DelegateIntrinsic<FunctionCallInfo> {
    override fun FunctionCallInfo.getArgs(): List<JsExpression> {
        return argumentsInfo.getTranslateArguments()
    }
    override fun FunctionCallInfo.getDescriptor(): CallableDescriptor {
        return callableDescriptor
    }
}

fun createFunctionCases(): CallCaseDispatcher<FunctionCallCase, FunctionCallInfo> {
    val caseDispatcher = CallCaseDispatcher<FunctionCallCase, FunctionCallInfo>()

    caseDispatcher.addCase { DelegateFunctionIntrinsic(it).intrinsic() }

    caseDispatcher.addCase(::DefaultCallCase) { true } // TODO: fix this
    return caseDispatcher
}