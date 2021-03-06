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

package org.jetbrains.kotlin.psi

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.addToStdlib.check
import java.util.*

public abstract class KtCodeFragment(
        private val _project: Project,
        name: String,
        text: CharSequence,
        imports: String?, // Should be separated by JetCodeFragment.IMPORT_SEPARATOR
        elementType: IElementType,
        private val context: PsiElement?
): KtFile((PsiManager.getInstance(_project) as PsiManagerEx).getFileManager().createFileViewProvider(LightVirtualFile(name, KotlinFileType.INSTANCE, text), true), false), JavaCodeFragment {

    private var viewProvider = super<KtFile>.getViewProvider() as SingleRootFileViewProvider
    private var myImports = LinkedHashSet<String>()
    private val additionalContextForLambda: PsiElement? by lazy {
        this.getCopyableUserData(ADDITIONAL_CONTEXT_FOR_LAMBDA)?.invoke()
    }

    init {
        getViewProvider().forceCachedPsi(this)
        init(TokenType.CODE_FRAGMENT, elementType)
        if (context != null) {
            initImports(imports)
        }
    }

    private var resolveScope: GlobalSearchScope? = null
    private var thisType: PsiType? = null
    private var superType: PsiType? = null
    private var exceptionHandler: JavaCodeFragment.ExceptionHandler? = null

    public abstract fun getContentElement(): KtElement?

    override fun forceResolveScope(scope: GlobalSearchScope?) {
        resolveScope = scope
    }

    override fun getForcedResolveScope() = resolveScope

    override fun isPhysical() = true

    override fun isValid() = true

    override fun getContext() = additionalContextForLambda ?: context

    override fun getResolveScope() = context?.getResolveScope() ?: super<KtFile>.getResolveScope()

    override fun clone(): KtCodeFragment {
        val clone = cloneImpl(calcTreeElement().clone() as FileElement) as KtCodeFragment
        clone.setOriginalFile(this)
        clone.myImports = myImports
        clone.viewProvider = SingleRootFileViewProvider(PsiManager.getInstance(_project), LightVirtualFile(getName(), KotlinFileType.INSTANCE, getText()), true)
        clone.viewProvider.forceCachedPsi(clone)
        return clone
    }

    override fun getViewProvider() = viewProvider

    override fun getThisType() = thisType

    override fun setThisType(psiType: PsiType?) {
        thisType = psiType
    }

    override fun getSuperType() = superType

    override fun setSuperType(superType: PsiType?) {
        this.superType = superType
    }

    override fun importsToString(): String {
        return myImports.join(IMPORT_SEPARATOR)
    }

    override fun addImportsFromString(imports: String?) {
        if (imports == null || imports.isEmpty()) return
        myImports.addAll(imports.split(IMPORT_SEPARATOR))
    }

    public fun importsAsImportList(): KtImportList? {
        return KtPsiFactory(this).createFile(myImports.join("\n")).getImportList()
    }

    override fun setVisibilityChecker(checker: JavaCodeFragment.VisibilityChecker?) { }

    override fun getVisibilityChecker() = JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE

    override fun setExceptionHandler(checker: JavaCodeFragment.ExceptionHandler?) {
        exceptionHandler = checker
    }

    override fun getExceptionHandler() = exceptionHandler

    override fun importClass(aClass: PsiClass?): Boolean {
        return true
    }

    private fun initImports(imports: String?) {
        if (imports != null && !imports.isEmpty()) {
            myImports.addAll(imports.split(IMPORT_SEPARATOR).map { it.check { it.startsWith("import ") } ?: "import $it" })
        }
    }

    companion object {
        public val IMPORT_SEPARATOR: String = ","
        public val RUNTIME_TYPE_EVALUATOR: Key<Function1<KtExpression, KotlinType?>> = Key.create("RUNTIME_TYPE_EVALUATOR")
        public val ADDITIONAL_CONTEXT_FOR_LAMBDA: Key<Function0<KtElement?>> = Key.create("ADDITIONAL_CONTEXT_FOR_LAMBDA")
    }
}
