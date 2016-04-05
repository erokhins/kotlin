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

package org.jetbrains.kotlin.resolve;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TemporaryBindingTraceImpl extends DelegatingBindingTrace implements TemporaryBindingTrace {

    @NotNull
    public static TemporaryBindingTraceImpl create(@NotNull BindingTrace trace, String debugName) {
        return new TemporaryBindingTraceImpl(trace, debugName);
    }

    @NotNull
    public static TemporaryBindingTraceImpl create(@NotNull BindingTrace trace, String debugName, @Nullable Object resolutionSubjectForMessage) {
        return create(trace, AnalyzingUtils.formDebugNameForBindingTrace(debugName, resolutionSubjectForMessage));
    }

    protected final BindingTrace trace;

    protected TemporaryBindingTraceImpl(@NotNull BindingTrace trace, String debugName) {
        super(trace.getBindingContext(), debugName);
        this.trace = trace;
    }

    @Override
    public void commit() {
        addOwnDataTo(trace);
        clear();
    }

    public void commit(@NotNull TraceEntryFilter filter, boolean commitDiagnostics) {
        addOwnDataTo(trace, filter, commitDiagnostics);
        clear();
    }
}
