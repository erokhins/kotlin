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

package org.jetbrains.k2js.translate;

import com.google.dart.compiler.backend.js.ast.JsName;
import com.google.dart.compiler.backend.js.ast.JsScope;

public final class LabelGenerator {
    private int nameCounter;
    private final char prefix;
    private final JsScope jsScope;

    public LabelGenerator(char prefix, JsScope jsScope) {
        this.prefix = prefix;
        this.jsScope = jsScope;
    }

    public String generate() {
        return prefix + Integer.toString(nameCounter++, 36);
    }

    public JsName getFreshName(String suggestName) {
        return jsScope.declareFreshName(suggestName);
    }
}
