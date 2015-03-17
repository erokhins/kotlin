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

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticUtils
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.JetBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.analyzeFully
import org.jetbrains.kotlin.idea.project.PluginJetFilesProvider
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.types.JetType
import org.jetbrains.kotlin.types.expressions.FunctionsTypingVisitor
import java.util.ArrayList

public class DeprecatedLambdaSyntaxFix(element: JetFunctionLiteralExpression) : JetIntentionAction<JetFunctionLiteralExpression>(element) {
    override fun getText() = JetBundle.message("migrate.lambda.syntax")
    override fun getFamilyName() = JetBundle.message("migrate.lambda.syntax.family")

    override fun invoke(project: Project, editor: Editor, file: JetFile) {
        LambdaWithDeprecatedSyntax(element).runFix(JetPsiFactory(project))
    }

    default object Factory : JetSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic)
                = (diagnostic.getPsiElement() as? JetFunctionLiteralExpression)?.let { DeprecatedLambdaSyntaxFix(it) }
    }
}

public class DeprecatedLambdaSyntaxInWholeProjectFix(element: JetFunctionLiteralExpression) : JetIntentionAction<JetFunctionLiteralExpression>(element) {
    override fun getText() = JetBundle.message("migrate.lambda.syntax.in.whole.project")
    override fun getFamilyName() = JetBundle.message("migrate.lambda.syntax.in.whole.project.family")

    override fun invoke(project: Project, editor: Editor, file: JetFile) {
        ProgressManager.getInstance().run(
                object : Task.Backgroundable(project, "Removing deprecated lambda syntax", false) {
                    override fun run(indicator: ProgressIndicator) {
                        val files = runReadAction { PluginJetFilesProvider.allFilesInProject(project) }
                        val psiFactory = JetPsiFactory(project)

                        for ((i, currentFile) in files.withIndex()) {
                            if (i > 0) break
                            
                            indicator.setText("Checking file $i of ${files.size()}...")
                            indicator.setText2(file.getVirtualFile().getPath())

                            try {
                                runWriteAction {
                                    val lambdas = ArrayList<LambdaWithDeprecatedSyntax>()
                                    currentFile.accept(LambdaCollectionVisitor(lambdas), 0)
                                    lambdas.sortBy { -it.level }.forEach {
                                        it.runFix(psiFactory)
                                    }
                                }
                            }
                            catch (e: Throwable) {
                                LOG.error(e)
                            }

                            indicator.setFraction((i + 1) / files.size().toDouble())
                        }
                    }
                }
        )

    }

    private class LambdaCollectionVisitor(val lambdas: MutableCollection<LambdaWithDeprecatedSyntax>) : JetTreeVisitor<Int>() {

        override fun visitFunctionLiteralExpression(functionLiteralExpression: JetFunctionLiteralExpression, data: Int): Void? {
            functionLiteralExpression.acceptChildren(this, data + 1)
            if (JetPsiUtil.isDeprecatedLambdaSyntax(functionLiteralExpression)) {
                lambdas.add(LambdaWithDeprecatedSyntax(functionLiteralExpression, data))
            }
            return null
        }

        override fun visitJetFile(file: JetFile, data: Int?): Void? {
            super.visitJetFile(file, data)
            file.acceptChildren(this, data)
            return null
        }
    }

    default object Factory : JetSingleIntentionActionFactory() {
        val LOG = Logger.getInstance(javaClass<DeprecatedLambdaSyntaxInWholeProjectFix>());

        override fun createAction(diagnostic: Diagnostic)
                = (diagnostic.getPsiElement() as? JetFunctionLiteralExpression)?.let { DeprecatedLambdaSyntaxInWholeProjectFix(it) }
    }

    override fun startInWriteAction() = false
}

private class LambdaWithDeprecatedSyntax(val functionLiteralExpression: JetFunctionLiteralExpression, val level: Int = 0) {
    val functionLiteral = functionLiteralExpression.getFunctionLiteral()
    val hasNoReturnAndReceiverType = !functionLiteral.hasDeclaredReturnType() && functionLiteral.getReceiverTypeReference() == null

    val functionLiteralType: JetType? = if (hasNoReturnAndReceiverType) null else {
        val type = functionLiteralExpression.analyze().get(BindingContext.EXPRESSION_TYPE, functionLiteralExpression)
        assert(type != null && KotlinBuiltIns.isFunctionOrExtensionFunctionType(type)) {
            "Broken function type for expression: ${functionLiteralExpression.getText()}, at: ${DiagnosticUtils.atLocation(functionLiteralExpression)}"
        }
        type
    }

    // you must run it under write action
    fun runFix(psiFactory: JetPsiFactory) {
        if (!JetPsiUtil.isDeprecatedLambdaSyntax(functionLiteralExpression)) return

        val functionLiteral = functionLiteralExpression.getFunctionLiteral()

        if (hasNoReturnAndReceiverType) {
            removeExternalParenthesesOnParameterList(functionLiteral, psiFactory)
        }
        else {
            val newFunctionExpression = convertToFunctionExpression(functionLiteralExpression, functionLiteralType!!, psiFactory)

            val callExpression = functionLiteralExpression.getParentOfTypeAndBranch<JetCallExpression> { getFunctionLiteralArguments().firstOrNull() }
            if (callExpression == null) {
                functionLiteralExpression.replace(newFunctionExpression)
            }
            else {
                val argumentList = callExpression.getValueArgumentList()
                if (argumentList != null) {
                    argumentList.pushParameter(newFunctionExpression, psiFactory)
                    functionLiteralExpression.delete()
                }
                else {
                    val newArgumentList = psiFactory.createCallArguments("()")
                    newArgumentList.pushParameter(newFunctionExpression, psiFactory)

                    callExpression.getFunctionLiteralArguments().first().replace(newArgumentList)
                }
            }
        }
    }

    private fun removeExternalParenthesesOnParameterList(functionLiteral: JetFunctionLiteral, psiFactory: JetPsiFactory) {
        val parameterList = functionLiteral.getValueParameterList()
        if (parameterList != null && parameterList.hasExternalParentheses()) {
            val oldParameterList = parameterList.getText()
            val newParameterList = oldParameterList.substring(1..oldParameterList.length() - 2)
            parameterList.replace(psiFactory.createFunctionLiteralParameterList(newParameterList))
        }
    }

    private fun JetElement.replaceWithReturn(psiFactory: JetPsiFactory) {
        if (this is JetReturnExpression) {
            return
        }
        else {
            replace(psiFactory.createReturn(getText()))
        }
    }

    private fun JetFunctionLiteral.getLabelOrAutoLabelName()
            = getParentOfTypeAndBranch<JetLabeledExpression>{ getBaseExpression() }?.getLabelName()

    private fun convertToFunctionExpression(
            functionLiteralExpression: JetFunctionLiteralExpression,
            functionLiteralType: JetType,
            psiFactory: JetPsiFactory
    ): JetNamedFunction {
        val functionLiteral = functionLiteralExpression.getFunctionLiteral()

        val functionName = functionLiteral.getLabelOrAutoLabelName()
        val parameterList = functionLiteral.getValueParameterList()?.getText()
        val receiverType = KotlinBuiltIns.getReceiverType(functionLiteralType)?.let {
            IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_IN_TYPES.renderType(it) }
        val returnType = KotlinBuiltIns.getReturnTypeFromFunctionType(functionLiteralType).let {
            if (KotlinBuiltIns.isUnit(it))
                null
            else
                IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_IN_TYPES.renderType(it)
        }

        val functionDeclaration = "fun " +
                                  (receiverType?.let { "$it." } ?: "") +
                                  (functionName ?: "") +
                                  (parameterList ?: "()") +
                                  (returnType?.let { ": $it" } ?: "")

        val functionWithEmptyBody = psiFactory.createFunction(functionDeclaration + " {}")

        val blockExpression = functionLiteral.getBodyExpression()
        if (blockExpression == null) return functionWithEmptyBody

        val statements = blockExpression.getStatements()
        if (statements.isEmpty()) return functionWithEmptyBody

        if (statements.size() == 1) {
            return psiFactory.createFunction(functionDeclaration + " = " + statements.first().getText())
        }

        // many statements
        if (returnType != null) statements.last().replaceWithReturn(psiFactory)

        return psiFactory.createFunction(functionDeclaration + "{ " + blockExpression.getText() + "}")
    }

    private fun JetValueArgumentList.pushParameter(expression: JetExpression, psiFactory: JetPsiFactory) {
        val rightParenthesis = getRightParenthesis()
        if (getArguments().isNotEmpty()) {
            addBefore(psiFactory.createComma(), rightParenthesis)
            addBefore(psiFactory.createWhiteSpace(), rightParenthesis)
        }
        addBefore(expression, rightParenthesis)
    }
}
