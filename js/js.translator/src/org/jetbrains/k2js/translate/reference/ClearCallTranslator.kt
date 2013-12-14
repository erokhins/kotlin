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

import org.jetbrains.jet.lang.resolve.calls.model.ResolvedCall
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor
import org.jetbrains.jet.lang.descriptors.CallableDescriptor
import com.google.dart.compiler.backend.js.ast.JsExpression
import org.jetbrains.jet.lang.descriptors.VariableDescriptor
import org.jetbrains.jet.lang.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.k2js.translate.context.TranslationContext


fun TranslationContext.buildCall(resolvedCall: ResolvedCall<out FunctionDescriptor>, receiver: JsExpression? = null): JsExpression {
    return buildCall(resolvedCall, receiver, null)
}

fun TranslationContext.buildGet(resolvedCall: ResolvedCall<out VariableDescriptor>, receiver: JsExpression? = null): JsExpression {
    throw UnsupportedOperationException()
}

fun TranslationContext.buildSet(resolvedCall: ResolvedCall<out VariableDescriptor>, setTo: JsExpression, receiver: JsExpression? = null): JsExpression {
    throw UnsupportedOperationException()
}



private fun TranslationContext.buildCall(resolvedCall: ResolvedCall<out FunctionDescriptor>, receiver1: JsExpression? = null, receiver2: JsExpression? = null): JsExpression {
    if (resolvedCall is VariableAsFunctionResolvedCall) {
        assert(receiver2 == null, "receiver2 for VariableAsFunctionResolvedCall must be null") // TODO: add debug info
        val variableCall = resolvedCall.getVariableCall()
        if (variableCall.expectedReceivers()) {
            val newReceiver = buildGet(variableCall, receiver1)
            return buildCall(resolvedCall.getFunctionCall(), newReceiver, null)
        } else {
            val newReceiver2 = buildGet(variableCall, null)
            return buildCall(resolvedCall.getFunctionCall(), receiver1, newReceiver2)
        }
    }

    throw UnsupportedOperationException()
}


open class FunctionCallBuilderAdapter(val callInfo: BaseFunctionCallInfo) {

    fun unsupported() : Exception {
        val stackTrace = Thread.currentThread().getStackTrace()
        val methodName = stackTrace.get(stackTrace.lastIndex - 1).getMethodName()
        val caseName = javaClass.getName()
        return UnsupportedOperationException("this case unsopported: $methodName, $caseName, $callInfo")
    }

    open fun noReceivers(): JsExpression {
        throw unsupported()
    }

    open fun thisObject(): JsExpression {
        throw unsupported()
    }

    open fun receiverArgument(): JsExpression {
        throw unsupported()
    }

    open fun bothReceivers(): JsExpression {
        throw unsupported()
    }

}
