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

package org.jetbrains.jet.j2k

import org.jetbrains.jet.lang.psi.JetPsiFactory
import org.jetbrains.jet.lang.resolve.java.AnalyzerFacadeForJVM
import org.jetbrains.jet.lang.resolve.BindingTraceContext
import org.jetbrains.jet.lang.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.jet.lang.resolve.name.Name
import org.jetbrains.jet.lang.resolve.java.mapping.JavaToKotlinClassMap
import com.intellij.openapi.project.Project
import org.jetbrains.jet.lang.diagnostics.Diagnostic
import org.jetbrains.jet.lang.diagnostics.Errors
import org.jetbrains.jet.lang.psi.JetSimpleNameExpression
import org.jetbrains.jet.lang.psi.JetUnaryExpression

class AfterConversionPass(val project: Project) {
    public fun run(kotlinCode: String): String {
        val kotlinFile = JetPsiFactory.createFile(project, kotlinCode)
        val analyzeExhaust = AnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
                project,
                listOf(kotlinFile),
                BindingTraceContext(),
                { true },
                ModuleDescriptorImpl(Name.special("<module>"), AnalyzerFacadeForJVM.DEFAULT_IMPORTS, JavaToKotlinClassMap.getInstance()),
                null,
                null
        )

        val problems = analyzeExhaust.getBindingContext().getDiagnostics()
        val fixes = problems.map {
            val fix = fixForProblem(it)
            if (fix != null) it.getPsiElement() to fix else null
        }.filterNotNull()

        if (fixes.isEmpty()) return kotlinCode

        for ((psiElement, fix) in fixes) {
            if (psiElement.isValid()) {
                fix()
            }
        }
        return kotlinFile.getText()!!
    }

    private fun fixForProblem(problem: Diagnostic): (() -> Unit)? {
        return when (problem.getFactory()) {
            Errors.UNNECESSARY_NOT_NULL_ASSERTION -> { () ->
                val exclExclOp = problem.getPsiElement() as JetSimpleNameExpression
                val exclExclExpr = exclExclOp.getParent() as JetUnaryExpression
                exclExclExpr.replace(exclExclExpr.getBaseExpression()!!)
            }

            else -> null
        }
    }
}