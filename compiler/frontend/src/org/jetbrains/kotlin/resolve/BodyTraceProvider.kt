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

package org.jetbrains.kotlin.resolve

import org.jetbrains.kotlin.resolve.BindingContext.*
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.asBackendResolvedCall
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.types.expressions.KotlinTypeInfo

interface BodyTraceProvider {
    fun createTraceForBodyAnalysis(parentTrace: BindingTrace): TemporaryBindingTrace

    object DEFAULT : BodyTraceProvider {
        override fun createTraceForBodyAnalysis(parentTrace: BindingTrace) =
                TemporaryBindingTraceImpl.create(parentTrace, "Trace for body analysis")
    }
}

object BodyTraceProviderForBackend : BodyTraceProvider {
    override fun createTraceForBodyAnalysis(parentTrace: BindingTrace): TemporaryBindingTrace = TemporaryTraceForBody(parentTrace)

    private val justDrop = setOf(EXPECTED_EXPRESSION_TYPE, EXPECTED_RETURN_TYPE, DATAFLOW_INFO_AFTER_CONDITION, QUALIFIER,
                                 THIS_TYPE_FOR_SUPER_EXPRESSION, SHORT_REFERENCE_TO_COMPANION_OBJECT, CONSTRAINT_SYSTEM_COMPLETER,
                                 AMBIGUOUS_REFERENCE_TARGET, DELEGATED_PROPERTY_PD_RESOLVED_CALL, SMARTCAST_NULL,
                                 IMPLICIT_RECEIVER_SMARTCAST, LEXICAL_SCOPE, SCRIPT_SCOPE, AUTO_CREATED_IT, PROCESSED,
                                 USED_AS_EXPRESSION, USED_AS_RESULT_OF_LAMBDA, UNREACHABLE_CODE, PRELIMINARY_VISITOR,
                                 IS_UNINITIALIZED, TYPE_PARAMETER, AMBIGUOUS_LABEL_TARGET, FQNAME_TO_CLASS_DESCRIPTOR)

    private class TemporaryTraceForBody(val parentTrace: BindingTrace):
            DelegatingBindingTrace(parentTrace.bindingContext, "Trace for body analysis"), TemporaryBindingTrace {

        override fun commit() {
            map.forEach { slice, key, value ->
                when (slice) {
                    RESOLVED_CALL -> parentTrace.record(slice, key, (value as ResolvedCall<*>).asBackendResolvedCall())
                    EXPRESSION_TYPE_INFO -> parentTrace.record(slice, key, KotlinTypeInfo((value as KotlinTypeInfo).type, DataFlowInfo.EMPTY))
                    in justDrop -> { /* do nothing */ }
                    else -> parentTrace.record(slice, key, value)
                }

                null
            }

            for (diagnostic in mutableDiagnostics.getOwnDiagnostics()) {
                parentTrace.report(diagnostic)
            }

            clear()
        }
    }
}