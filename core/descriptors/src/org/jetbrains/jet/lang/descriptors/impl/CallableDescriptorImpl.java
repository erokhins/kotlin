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

package org.jetbrains.jet.lang.descriptors.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.descriptors.annotations.Annotations;
import org.jetbrains.jet.lang.resolve.DescriptorFactory;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.jet.lang.types.JetType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public abstract class CallableDescriptorImpl extends DeclarationDescriptorNonRootImpl implements CallableDescriptor {
    private List<TypeParameterDescriptor> typeParameters;
    private List<ValueParameterDescriptor> unsubstitutedValueParameters;
    protected JetType returnType;
    private ReceiverParameterDescriptor extensionReceiverParameter;
    private ReceiverParameterDescriptor dispatchReceiverParameter;
    private Visibility visibility;
    protected final Set<CallableDescriptor> overriddenDescriptors = new LinkedHashSet<CallableDescriptor>(); // LinkedHashSet is essential here

    protected CallableDescriptorImpl(
            @NotNull DeclarationDescriptor containingDeclaration,
            @NotNull Annotations annotations,
            @NotNull Name name,
            @NotNull SourceElement source
    ) {
        super(containingDeclaration, annotations, name, source);
    }

    @NotNull
    protected CallableDescriptorImpl initialize(
            @Nullable JetType receiverParameterType,
            @Nullable ReceiverParameterDescriptor dispatchReceiverParameter,
            @NotNull List<? extends TypeParameterDescriptor> typeParameters,
            @NotNull List<ValueParameterDescriptor> unsubstitutedValueParameters,
            @Nullable JetType unsubstitutedReturnType,
            @NotNull Visibility visibility
    ) {
        this.typeParameters = new ArrayList<TypeParameterDescriptor>(typeParameters);
        this.unsubstitutedValueParameters = unsubstitutedValueParameters;
        this.returnType = unsubstitutedReturnType;
        this.visibility = visibility;
        this.extensionReceiverParameter = DescriptorFactory.createExtensionReceiverParameterForCallable(this, receiverParameterType);
        this.dispatchReceiverParameter = dispatchReceiverParameter;
        
        return this;
    }

    public void setVisibility(@NotNull Visibility visibility) {
        this.visibility = visibility;
    }

    public void setReturnType(@NotNull JetType returnType) {
        if (this.returnType != null) {
            // TODO: uncomment and fix tests
            //throw new IllegalStateException("returnType already set");
        }
        this.returnType = returnType;
    }

    @Nullable
    @Override
    public ReceiverParameterDescriptor getExtensionReceiverParameter() {
        return extensionReceiverParameter;
    }

    @Nullable
    @Override
    public ReceiverParameterDescriptor getDispatchReceiverParameter() {
        return dispatchReceiverParameter;
    }

    @NotNull
    @Override
    public Set<? extends CallableDescriptor> getOverriddenDescriptors() {
        return overriddenDescriptors;
    }

    @NotNull
    @Override
    public Visibility getVisibility() {
        return visibility;
    }

    @Override
    @NotNull
    public List<TypeParameterDescriptor> getTypeParameters() {
        return typeParameters;
    }

    @Override
    @NotNull
    public List<ValueParameterDescriptor> getValueParameters() {
        return unsubstitutedValueParameters;
    }

    @Override
    public JetType getReturnType() {
        return returnType;
    }

    @NotNull
    @Override
    public abstract CallableDescriptor getOriginal();
}
