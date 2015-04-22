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

package profiling

import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.profiling.JetCounter
import org.jetbrains.kotlin.utils.profiling.ProfilingAgent
import java.util.ArrayList

val fileCounter = JetCounter("Files")
val elementCounter = JetCounter("Elements")
val expressionCounter = JetCounter("Expressions")
val functionCounter = JetCounter("Functions")

val calls = JetCounter("Call expression")

object JetCountingVisitor : JetTreeVisitorVoid() {
    override fun visitExpression(expression: JetExpression) {
        super.visitExpression(expression)
        expressionCounter.touch()
    }

    override fun visitJetElement(element: JetElement) {
        super.visitJetElement(element)
        elementCounter.touch()
    }

    override fun visitNamedFunction(function: JetNamedFunction) {
        super.visitNamedFunction(function)
        functionCounter.touch()
    }

    override fun visitJetFile(file: JetFile) {
        super.visitJetFile(file)
        fileCounter.touch()
    }

    override fun visitCallExpression(expression: JetCallExpression) {
        super.visitCallExpression(expression)
        calls.touch()
    }

    public fun visitFiles(jetFiles: Collection<JetFile>) {
        jetFiles.forEach { it.accept(this) }
    }
}
