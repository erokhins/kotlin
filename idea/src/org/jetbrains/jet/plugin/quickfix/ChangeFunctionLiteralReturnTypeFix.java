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

package org.jetbrains.jet.plugin.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.diagnostics.Diagnostic;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.types.JetType;
import org.jetbrains.jet.lang.types.TypeProjection;
import org.jetbrains.jet.lang.types.TypeUtils;
import org.jetbrains.jet.lang.types.checker.JetTypeChecker;
import org.jetbrains.jet.lang.types.lang.KotlinBuiltIns;
import org.jetbrains.jet.plugin.JetBundle;
import org.jetbrains.jet.plugin.project.AnalyzerFacadeWithCache;
import org.jetbrains.jet.renderer.DescriptorRenderer;

import java.util.LinkedList;
import java.util.List;

public class ChangeFunctionLiteralReturnTypeFix extends JetIntentionAction<JetFunctionLiteralExpression> {
    private final String renderedType;

    private ChangeVariableTypeFix changePropertyTypeFix = null;
    private ChangeFunctionParameterTypeFix changeParameterTypeFix = null;

    public ChangeFunctionLiteralReturnTypeFix(@NotNull JetFunctionLiteralExpression element, @NotNull JetType type) {
        super(element);
        renderedType = DescriptorRenderer.SHORT_NAMES_IN_TYPES.renderType(type);

        BindingContext context = AnalyzerFacadeWithCache.analyzeFileWithCache((JetFile) element.getContainingFile()).getBindingContext();
        JetType functionLiteralType = context.get(BindingContext.EXPRESSION_TYPE, element);
        assert functionLiteralType != null : "Type of function literal not available in binding context";

        ClassDescriptor functionClass = KotlinBuiltIns.getInstance().getFunction(functionLiteralType.getArguments().size() - 1);
        List<JetType> functionClassTypeParameters = new LinkedList<JetType>();
        for (TypeProjection typeProjection: functionLiteralType.getArguments()) {
            functionClassTypeParameters.add(typeProjection.getType());
        }
        // Replacing return type:
        functionClassTypeParameters.remove(functionClassTypeParameters.size() - 1);
        functionClassTypeParameters.add(type);
        JetType eventualFunctionLiteralType = TypeUtils.substituteParameters(functionClass, functionClassTypeParameters);

        JetProperty correspondingProperty = PsiTreeUtil.getParentOfType(element, JetProperty.class);
        if (correspondingProperty != null && QuickFixUtil.canEvaluateTo(correspondingProperty.getInitializer(), element)) {
            JetTypeReference correspondingPropertyTypeRef = correspondingProperty.getTypeRef();
            JetType propertyType = context.get(BindingContext.TYPE, correspondingPropertyTypeRef);
            if (propertyType != null && !JetTypeChecker.INSTANCE.isSubtypeOf(eventualFunctionLiteralType, propertyType)) {
                changePropertyTypeFix = new ChangeVariableTypeFix(correspondingProperty, eventualFunctionLiteralType);
            }
        }
        else {
            JetParameter correspondingParameter = QuickFixUtil.getFunctionParameterCorrespondingToFunctionLiteralPassedOutsideArgumentList(element);
            JetTypeReference correspondingParameterTypeRef = correspondingParameter == null ? null : correspondingParameter.getTypeReference();
            JetType parameterType = context.get(BindingContext.TYPE, correspondingParameterTypeRef);
            if (parameterType != null && !JetTypeChecker.INSTANCE.isSubtypeOf(eventualFunctionLiteralType, parameterType)) {
                assert correspondingParameter != null;
                changeParameterTypeFix = new ChangeFunctionParameterTypeFix(correspondingParameter, eventualFunctionLiteralType);
            }
        }
    }

    @NotNull
    @Override
    public String getText() {
        if (changePropertyTypeFix != null) {
            return changePropertyTypeFix.getText();
        }
        if (changeParameterTypeFix != null) {
            return changeParameterTypeFix.getText();
        }
        return JetBundle.message("change.function.literal.return.type", renderedType);
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return JetBundle.message("change.type.family");
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, JetFile file) throws IncorrectOperationException {
        JetTypeReference returnTypeRef = element.getFunctionLiteral().getReturnTypeRef();
        if (returnTypeRef != null) {
            returnTypeRef.replace(JetPsiFactory.createType(project, renderedType));
        }
        if (changePropertyTypeFix != null) {
            changePropertyTypeFix.invoke(project, editor, file);
        }
        if (changeParameterTypeFix != null) {
            changeParameterTypeFix.invoke(project, editor, file);
        }
    }

    @NotNull
    public static JetIntentionActionFactory createFactoryForExpectedOrAssignmentTypeMismatch() {
        return new JetIntentionActionFactory() {
            @Nullable
            @Override
            public IntentionAction createAction(Diagnostic diagnostic) {
                JetFunctionLiteralExpression functionLiteralExpression = QuickFixUtil.getParentElementOfType(diagnostic, JetFunctionLiteralExpression.class);
                assert functionLiteralExpression != null : "ASSIGNMENT/EXPECTED_TYPE_MISMATCH reported outside any function literal";
                return new ChangeFunctionLiteralReturnTypeFix(functionLiteralExpression, KotlinBuiltIns.getInstance().getUnitType());
            }
        };
    }
}
