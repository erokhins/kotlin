/*
 * Copyright 2010-2014 JetBrains s.r.o.
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

package org.jetbrains.jet.lang.diagnostics.rendering;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.diagnostics.DiagnosticWithParameters1;

public class DiagnosticWithParametersRenderer<A> extends AbstractDiagnosticWithParametersRenderer<DiagnosticWithParameters1<?,A>> {
    @NotNull private final Renderers.MultiRenderer<? super A> renderersForA;

    public DiagnosticWithParametersRenderer(@NotNull String message, @NotNull Renderers.MultiRenderer<? super A> renderersForA) {
        super(message);
        this.renderersForA = renderersForA;
    }

    @NotNull
    @Override
    public Object[] renderParameters(@NotNull DiagnosticWithParameters1<?, A> diagnostic) {
        return renderersForA.render(diagnostic.getA());
    }
}
