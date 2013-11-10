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

package org.jetbrains.k2js.translate.expression;

import com.google.dart.compiler.backend.js.ast.*;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Trinity;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.descriptors.ConstructorDescriptor;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor;
import org.jetbrains.jet.lang.psi.JetClassOrObject;
import org.jetbrains.jet.lang.psi.JetDeclarationWithBody;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.k2js.translate.LabelGenerator;
import org.jetbrains.k2js.translate.context.AliasingContext;
import org.jetbrains.k2js.translate.context.Namer;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.context.UsageTracker;
import org.jetbrains.k2js.translate.declaration.ClassTranslator;
import org.jetbrains.k2js.translate.general.AbstractTranslator;

import java.util.List;

import static org.jetbrains.k2js.translate.utils.FunctionBodyTranslator.translateFunctionBody;
import static org.jetbrains.k2js.translate.utils.JsDescriptorUtils.getExpectedReceiverDescriptor;
import static org.jetbrains.k2js.translate.utils.JsDescriptorUtils.getNameForNestedClassOrObject;

public class LiteralFunctionTranslator extends AbstractTranslator {
    private final Stack<NotNullLazyValue<Trinity<List<JsPropertyInitializer>, LabelGenerator, JsExpression>>> definitionPlaces =
            new Stack<NotNullLazyValue<Trinity<List<JsPropertyInitializer>, LabelGenerator, JsExpression>>>();
    private NotNullLazyValue<Trinity<List<JsPropertyInitializer>, LabelGenerator, JsExpression>> definitionPlace;

    private NotNullLazyValue<Trinity<List<JsPropertyInitializer>, LabelGenerator, JsExpression>> fileDefinitionPlace;

    public LiteralFunctionTranslator(@NotNull TranslationContext context) {
        super(context);
    }

    public static Trinity<List<JsPropertyInitializer>, LabelGenerator, JsExpression> createPlace(@NotNull List<JsPropertyInitializer> list,
            @NotNull JsExpression reference,
            @NotNull JsScope jsScope) {
        return Trinity.create(list, new LabelGenerator('f', jsScope), reference);
    }

    public void setFileDefinitionPlace(NotNullLazyValue<Trinity<List<JsPropertyInitializer>, LabelGenerator, JsExpression>> fileDefinitionPlace) {
        setDefinitionPlace(fileDefinitionPlace);
        this.fileDefinitionPlace = definitionPlace;
    }

    public void setDefinitionPlace(@Nullable NotNullLazyValue<Trinity<List<JsPropertyInitializer>, LabelGenerator, JsExpression>> place) {
        if (place == null) {
            definitionPlaces.pop();
            definitionPlace = definitionPlaces.isEmpty() ? null : definitionPlaces.peek();
        }
        else {
            definitionPlaces.push(place);
            definitionPlace = place;
        }
    }

    public JsExpression translate(@NotNull JetDeclarationWithBody declaration, @NotNull FunctionDescriptor descriptor, @NotNull TranslationContext outerContext) {
        JsFunction fun = createFunction();
        TranslationContext funContext;
        boolean asInner;
        ClassDescriptor outerClass;
        AliasingContext aliasingContext;
        DeclarationDescriptor receiverDescriptor = getExpectedReceiverDescriptor(descriptor);
        JsName receiverName;
        if (receiverDescriptor == null) {
            receiverName = null;
            aliasingContext = null;
        }
        else {
            receiverName = fun.getScope().declareName(Namer.getReceiverParameterName());
            aliasingContext = outerContext.aliasingContext().inner(receiverDescriptor, receiverName.makeRef());
        }

        if (descriptor.getContainingDeclaration() instanceof ConstructorDescriptor) {
            // KT-2388
            asInner = true;
            fun.setName(fun.getScope().declareName(Namer.CALLEE_NAME));
            outerClass = (ClassDescriptor) descriptor.getContainingDeclaration().getContainingDeclaration();
            assert outerClass != null;

            if (receiverDescriptor == null) {
                aliasingContext = outerContext.aliasingContext().notShareableThisAliased(outerClass, new JsNameRef("o", fun.getName().makeRef()));
            }
        }
        else {
            outerClass = null;
            asInner = DescriptorUtils.isTopLevelDeclaration(descriptor);
        }

        funContext = outerContext.newFunctionBody(fun, aliasingContext,
                                                  new UsageTracker(descriptor, outerContext.usageTracker(), outerClass));

        fun.getBody().getStatements().addAll(translateFunctionBody(descriptor, declaration, funContext).getStatements());

        InnerFunctionTranslator translator = null;
        if (!asInner) {
            translator = new InnerFunctionTranslator(descriptor, funContext, fun);
        }

        if (asInner) {
            addRegularParameters(descriptor, fun, funContext, receiverName);
            if (outerClass != null) {
                UsageTracker usageTracker = funContext.usageTracker();
                assert usageTracker != null;
                if (usageTracker.isUsed()) {
                    return new JsInvocation(context().namer().kotlin("assignOwner"), fun, JsLiteral.THIS);
                }
                else {
                    fun.setName(null);
                }
            }

            return fun;
        }

        JsExpression result = translator.translate(createReference(fun), outerContext);
        addRegularParameters(descriptor, fun, funContext, receiverName);
        return result;
    }

    private JsNameRef createReference(JsFunction fun) {
        Trinity<List<JsPropertyInitializer>, LabelGenerator, JsExpression> place = definitionPlace.getValue();
        JsNameRef nameRef = new JsNameRef(place.second.generate(), place.third);
        place.first.add(new JsPropertyInitializer(nameRef, fun));
        return nameRef;
    }

    private JsNameRef createReference(
            @NotNull JsInvocation classInvocation,
            @NotNull ClassDescriptor objectDescriptor
    ) {
        Trinity<List<JsPropertyInitializer>, LabelGenerator, JsExpression> place = fileDefinitionPlace.getValue();
        String suggestName = getNameForNestedClassOrObject(objectDescriptor);
        JsNameRef nameRef = new JsNameRef(place.second.getFreshName(suggestName), place.third); // TODO: check
        place.first.add(new JsPropertyInitializer(nameRef, classInvocation));
        return nameRef;
    }

    private static void addRegularParameters(
            @NotNull FunctionDescriptor descriptor,
            @NotNull JsFunction fun,
            @NotNull TranslationContext funContext,
            @Nullable JsName receiverName
    ) {
        if (receiverName != null) {
            fun.getParameters().add(new JsParameter(receiverName));
        }
        FunctionTranslator.addParameters(fun.getParameters(), descriptor, funContext);
    }

    private JsFunction createFunction() {
        return new JsFunction(context().scope(), new JsBlock());
    }

    @NotNull
    private TranslationContext getNormalizeTranslationContext(@Nullable ClassDescriptor outerClass,
            @NotNull TranslationContext outerClassContext) {
        if (outerClass == null) {
            return outerClassContext;
        }
        JsNameRef outerClassRef = context().getQualifiedReference(outerClass);
        if (!outerClass.getKind().isObject()) {
            outerClassRef = Namer.getClassObjectAccessor(outerClassRef); // TODO: fix hack (this is static access)
        }
        return outerClassContext.innerContextWithThisAliased(outerClass, outerClassRef);
    }

    public JsExpression translateObject(
            @Nullable ClassDescriptor outerClass,
            @NotNull TranslationContext outerClassContext,
            @NotNull JetClassOrObject objectDeclaration,
            @NotNull ClassDescriptor objectDescriptor,
            @NotNull ClassTranslator classTranslator
    ) {
        JsInvocation classInvocation = classTranslator.translate(getNormalizeTranslationContext(outerClass, outerClassContext));
        JsNameRef classRef = createReference(classInvocation, objectDescriptor);
        return new JsNew(classRef);

        //
        //JsFunction fun = createFunction();
        //JsNameRef outerClassRef;
        ////outerClassRef = fun.getScope().declareName(Namer.OUTER_CLASS_NAME).makeRef();
        //outerClassRef = context().getQualifiedReference(outerClass);
        //UsageTracker usageTracker = new UsageTracker(objectDescriptor, outerClassContext.usageTracker(), outerClass);
        //AliasingContext aliasingContext = outerClassContext.aliasingContext().inner(outerClass, outerClassRef);
        //TranslationContext funContext = outerClassContext.newFunctionBody(fun, aliasingContext, usageTracker);
        //
        //fun.getBody().getStatements().add(new JsReturn(classTranslator.translate(funContext)));
        //JetClassBody body = objectDeclaration.getBody();
        //assert body != null;
        //return new InnerObjectTranslator(funContext, fun).translate(createReference(fun), null);
    }
}
