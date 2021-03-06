/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.cli.jvm.compiler;

import org.jetbrains.kotlin.builtins.KotlinBuiltIns;
import org.jetbrains.kotlin.name.Name;
import org.jetbrains.kotlin.resolve.AnalyzerScriptParameter;
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatform;
import org.jetbrains.kotlin.types.KotlinType;

import java.util.Collections;
import java.util.List;

import static org.jetbrains.kotlin.types.Variance.INVARIANT;

public class CommandLineScriptUtils {
    private static final Name ARGS_NAME = Name.identifier("args");

    private CommandLineScriptUtils() {
    }

    public static List<AnalyzerScriptParameter> scriptParameters() {
        KotlinBuiltIns builtIns = JvmPlatform.INSTANCE$.getBuiltIns();
        KotlinType arrayOfStrings = builtIns.getArrayType(INVARIANT, builtIns.getStringType());
        AnalyzerScriptParameter argsParameter = new AnalyzerScriptParameter(ARGS_NAME, arrayOfStrings);
        return Collections.singletonList(argsParameter);
    }
}
