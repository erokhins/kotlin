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

import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.asBackendResolvedCall

interface BodyTraceProvider {
    fun createTraceForBodyAnalysis(parentTrace: BindingTrace): TemporaryBindingTrace

    object DEFAULT : BodyTraceProvider {
        override fun createTraceForBodyAnalysis(parentTrace: BindingTrace) =
                TemporaryBindingTraceImpl.create(parentTrace, "Trace for body analysis")
    }
}

object BodyTraceProviderForBackend : BodyTraceProvider {
    override fun createTraceForBodyAnalysis(parentTrace: BindingTrace): TemporaryBindingTrace = TemporaryTraceForBody(parentTrace)

    private class TemporaryTraceForBody(val parentTrace: BindingTrace):
            DelegatingBindingTrace(parentTrace.bindingContext, "Trace for body analysis"), TemporaryBindingTrace {

        override fun commit() {
            map.forEach { slice, key, value ->
                if (key == BindingContext.RESOLVED_CALL) {
                    parentTrace.record(slice, key, (value as ResolvedCall<*>).asBackendResolvedCall())
                }
                else {
                    parentTrace.record(slice, key, value)
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