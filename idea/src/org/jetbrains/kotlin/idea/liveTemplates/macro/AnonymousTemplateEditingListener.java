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

package org.jetbrains.kotlin.idea.liveTemplates.macro;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.kotlin.descriptors.ClassDescriptor;
import org.jetbrains.kotlin.descriptors.ClassKind;
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor;
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor;
import org.jetbrains.kotlin.idea.caches.resolve.ResolutionUtils;
import org.jetbrains.kotlin.idea.core.overrideImplement.ImplementMembersHandler;
import org.jetbrains.kotlin.psi.KtReferenceExpression;
import org.jetbrains.kotlin.resolve.BindingContext;
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode;

class AnonymousTemplateEditingListener extends TemplateEditingAdapter {
    private KtReferenceExpression classRef;
    private ClassDescriptor classDescriptor;
    private final Editor editor;
    private final PsiFile psiFile;

    private static final Key<AnonymousTemplateEditingListener> LISTENER_KEY = Key.create("kotlin.AnonymousTemplateEditingListener");
    
    public AnonymousTemplateEditingListener(PsiFile psiFile, Editor editor) {
        this.psiFile = psiFile;
        this.editor = editor;
    }

    @Override
    public void currentVariableChanged(TemplateState templateState, Template template, int oldIndex, int newIndex) {
        assert templateState.getTemplate() != null;
        TextRange variableRange = templateState.getVariableRange("SUPERTYPE");
        if (variableRange == null) return;
        PsiElement name = psiFile.findElementAt(variableRange.getStartOffset());
        if (name != null && name.getParent() instanceof KtReferenceExpression) {
            KtReferenceExpression ref = (KtReferenceExpression) name.getParent();
            DeclarationDescriptor descriptor = ResolutionUtils.analyze(ref, BodyResolveMode.FULL).get(BindingContext.REFERENCE_TARGET, ref);
            if (descriptor instanceof ClassDescriptor) {
                classRef = ref;
                classDescriptor = (ClassDescriptor) descriptor;
            }
        }
    }

    @Override
    public void templateFinished(Template template, boolean brokenOff) {
        editor.putUserData(LISTENER_KEY, null);
        if (brokenOff) {
            return;
        }

        if (classDescriptor != null) {
            if (classDescriptor.getKind() == ClassKind.CLASS) {
                int placeToInsert = classRef.getTextRange().getEndOffset();
                PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile).insertString(placeToInsert, "()");

                boolean hasConstructorsParameters = false;
                for (ConstructorDescriptor cd : classDescriptor.getConstructors()) {
                    // TODO check for visibility
                    hasConstructorsParameters |= cd.getValueParameters().size() != 0;
                }

                if (hasConstructorsParameters) {
                    editor.getCaretModel().moveToOffset(placeToInsert + 1);
                }
            }

            new ImplementMembersHandler().invoke(psiFile.getProject(), editor, psiFile, true);
        }
    }
    
    static void registerListener(Editor editor, Project project) {
        if (editor.getUserData(LISTENER_KEY) != null) {
            return;
        }
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        assert psiFile != null;
        TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
        if (templateState != null) {
            AnonymousTemplateEditingListener listener = new AnonymousTemplateEditingListener(psiFile, editor);
            editor.putUserData(LISTENER_KEY, listener);
            templateState.addTemplateStateListener(listener);
        }
    }
}
