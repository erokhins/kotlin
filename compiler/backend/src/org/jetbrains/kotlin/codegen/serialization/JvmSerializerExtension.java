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

package org.jetbrains.kotlin.codegen.serialization;

import com.google.protobuf.MessageLite;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.codegen.state.JetTypeMapper;
import org.jetbrains.kotlin.descriptors.*;
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor;
import org.jetbrains.kotlin.load.java.lazy.types.RawTypeCapabilities;
import org.jetbrains.kotlin.name.ClassId;
import org.jetbrains.kotlin.serialization.AnnotationSerializer;
import org.jetbrains.kotlin.serialization.ProtoBuf;
import org.jetbrains.kotlin.serialization.SerializerExtension;
import org.jetbrains.kotlin.serialization.StringTable;
import org.jetbrains.kotlin.serialization.jvm.JvmProtoBuf;
import org.jetbrains.kotlin.serialization.jvm.JvmProtoBufUtil;
import org.jetbrains.kotlin.types.KotlinType;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.commons.Method;

import static org.jetbrains.kotlin.codegen.serialization.JvmSerializationBindings.*;

public class JvmSerializerExtension extends SerializerExtension {
    private final JvmSerializationBindings bindings;
    private final StringTable stringTable;
    private final AnnotationSerializer annotationSerializer;
    private final boolean useTypeTable;

    public JvmSerializerExtension(@NotNull JvmSerializationBindings bindings, @NotNull JetTypeMapper typeMapper, boolean useTypeTable) {
        this.bindings = bindings;
        this.stringTable = new JvmStringTable(typeMapper);
        this.annotationSerializer = new AnnotationSerializer(stringTable);
        this.useTypeTable = useTypeTable;
    }

    @NotNull
    @Override
    public StringTable getStringTable() {
        return stringTable;
    }

    @Override
    public boolean shouldUseTypeTable() {
        return useTypeTable;
    }

    @Override
    public void serializeValueParameter(@NotNull ValueParameterDescriptor descriptor, @NotNull ProtoBuf.ValueParameter.Builder proto) {
        Integer index = bindings.get(INDEX_FOR_VALUE_PARAMETER, descriptor);
        if (index != null) {
            proto.setExtension(JvmProtoBuf.index, index);
        }
    }

    @Override
    public void serializeType(@NotNull KotlinType type, @NotNull ProtoBuf.Type.Builder proto) {
        // TODO: don't store type annotations in our binary metadata on Java 8, use *TypeAnnotations attributes instead
        for (AnnotationDescriptor annotation : type.getAnnotations()) {
            proto.addExtension(JvmProtoBuf.typeAnnotation, annotationSerializer.serializeAnnotation(annotation));
        }

        if (type.getCapabilities() instanceof RawTypeCapabilities) {
            proto.setExtension(JvmProtoBuf.isRaw, true);
        }
    }

    @Override
    public void serializeTypeParameter(
            @NotNull TypeParameterDescriptor typeParameter, @NotNull ProtoBuf.TypeParameter.Builder proto
    ) {
        for (AnnotationDescriptor annotation : typeParameter.getAnnotations()) {
            proto.addExtension(JvmProtoBuf.typeParameterAnnotation, annotationSerializer.serializeAnnotation(annotation));
        }
    }

    @Override
    public void serializeConstructor(@NotNull ConstructorDescriptor descriptor, @NotNull ProtoBuf.Constructor.Builder proto) {
        Method method = bindings.get(METHOD_FOR_FUNCTION, descriptor);
        if (method != null) {
            JvmProtoBuf.JvmMethodSignature signature = new SignatureSerializer().methodSignature(descriptor, method);
            if (signature != null) {
                proto.setExtension(JvmProtoBuf.constructorSignature, signature);
            }
        }

        saveImplClassName(descriptor, proto);
    }

    @Override
    public void serializeFunction(@NotNull FunctionDescriptor descriptor, @NotNull ProtoBuf.Function.Builder proto) {
        Method method = bindings.get(METHOD_FOR_FUNCTION, descriptor);
        if (method != null) {
            JvmProtoBuf.JvmMethodSignature signature = new SignatureSerializer().methodSignature(descriptor, method);
            if (signature != null) {
                proto.setExtension(JvmProtoBuf.methodSignature, signature);
            }
        }

        saveImplClassName(descriptor, proto);
    }

    @Override
    public void serializeProperty(@NotNull PropertyDescriptor descriptor, @NotNull ProtoBuf.Property.Builder proto) {
        SignatureSerializer signatureSerializer = new SignatureSerializer();

        PropertyGetterDescriptor getter = descriptor.getGetter();
        PropertySetterDescriptor setter = descriptor.getSetter();
        Method getterMethod = getter == null ? null : bindings.get(METHOD_FOR_FUNCTION, getter);
        Method setterMethod = setter == null ? null : bindings.get(METHOD_FOR_FUNCTION, setter);

        Pair<Type, String> field = bindings.get(FIELD_FOR_PROPERTY, descriptor);
        String fieldName;
        String fieldDesc;
        boolean isStaticInOuter;
        if (field != null) {
            fieldName = field.second;
            fieldDesc = field.first.getDescriptor();
            isStaticInOuter = bindings.get(STATIC_FIELD_IN_OUTER_CLASS, descriptor);
        }
        else {
            fieldName = null;
            fieldDesc = null;
            isStaticInOuter = false;
        }

        Method syntheticMethod = bindings.get(SYNTHETIC_METHOD_FOR_PROPERTY, descriptor);

        JvmProtoBuf.JvmPropertySignature signature = signatureSerializer.propertySignature(
                descriptor, fieldName, fieldDesc, isStaticInOuter,
                syntheticMethod != null ? signatureSerializer.methodSignature(null, syntheticMethod) : null,
                getterMethod != null ? signatureSerializer.methodSignature(null, getterMethod) : null,
                setterMethod != null ? signatureSerializer.methodSignature(null, setterMethod) : null
        );

        proto.setExtension(JvmProtoBuf.propertySignature, signature);

        saveImplClassName(descriptor, proto);
    }

    private void saveImplClassName(@NotNull CallableMemberDescriptor callable, @NotNull MessageLite.Builder proto) {
        String name = bindings.get(IMPL_CLASS_NAME_FOR_CALLABLE, callable);
        if (name == null) return;

        int index = stringTable.getStringIndex(name);
        if (proto instanceof ProtoBuf.Function.Builder) {
            ((ProtoBuf.Function.Builder) proto).setExtension(JvmProtoBuf.methodImplClassName, index);
        }
        else if (proto instanceof ProtoBuf.Property.Builder) {
            ((ProtoBuf.Property.Builder) proto).setExtension(JvmProtoBuf.propertyImplClassName, index);
        }
    }

    private class SignatureSerializer {
        @Nullable
        public JvmProtoBuf.JvmMethodSignature methodSignature(@Nullable FunctionDescriptor descriptor, @NotNull Method method) {
            JvmProtoBuf.JvmMethodSignature.Builder builder = JvmProtoBuf.JvmMethodSignature.newBuilder();
            if (descriptor == null || !descriptor.getName().asString().equals(method.getName())) {
                builder.setName(stringTable.getStringIndex(method.getName()));
            }
            if (descriptor == null || requiresSignature(descriptor, method.getDescriptor())) {
                builder.setDesc(stringTable.getStringIndex(method.getDescriptor()));
            }
            return builder.hasName() || builder.hasDesc() ? builder.build() : null;
        }

        // We don't write those signatures which can be trivially reconstructed from already serialized data
        // TODO: make JvmStringTable implement NameResolver and use JvmProtoBufUtil#getJvmMethodSignature instead
        private boolean requiresSignature(@NotNull FunctionDescriptor descriptor, @NotNull String desc) {
            StringBuilder sb = new StringBuilder();
            sb.append("(");
            ReceiverParameterDescriptor receiverParameter = descriptor.getExtensionReceiverParameter();
            if (receiverParameter != null) {
                String receiverDesc = mapTypeDefault(receiverParameter.getValue().getType());
                if (receiverDesc == null) return true;
                sb.append(receiverDesc);
            }

            for (ValueParameterDescriptor valueParameter : descriptor.getValueParameters()) {
                String paramDesc = mapTypeDefault(valueParameter.getType());
                if (paramDesc == null) return true;
                sb.append(paramDesc);
            }

            sb.append(")");

            KotlinType returnType = descriptor.getReturnType();
            String returnTypeDesc = returnType == null ? "V" : mapTypeDefault(returnType);
            if (returnTypeDesc == null) return true;
            sb.append(returnTypeDesc);

            return !sb.toString().equals(desc);
        }

        private boolean requiresSignature(@NotNull PropertyDescriptor descriptor, @NotNull String desc) {
            return !desc.equals(mapTypeDefault(descriptor.getType()));
        }

        @Nullable
        private String mapTypeDefault(@NotNull KotlinType type) {
            ClassifierDescriptor classifier = type.getConstructor().getDeclarationDescriptor();
            if (!(classifier instanceof ClassDescriptor)) return null;
            ClassId classId = classId((ClassDescriptor) classifier);
            return classId == null ? null : JvmProtoBufUtil.mapClassIdDefault(classId);
        }

        @Nullable
        private ClassId classId(@NotNull ClassDescriptor descriptor) {
            DeclarationDescriptor container = descriptor.getContainingDeclaration();
            if (container instanceof PackageFragmentDescriptor) {
                return ClassId.topLevel(((PackageFragmentDescriptor) container).getFqName().child(descriptor.getName()));
            }
            else if (container instanceof ClassDescriptor) {
                ClassId outerClassId = classId((ClassDescriptor) container);
                return outerClassId == null ? null : outerClassId.createNestedClassId(descriptor.getName());
            }
            else {
                return null;
            }
        }

        @NotNull
        public JvmProtoBuf.JvmPropertySignature propertySignature(
                @NotNull PropertyDescriptor descriptor,
                @Nullable String fieldName,
                @Nullable String fieldDesc,
                boolean isStaticInOuter,
                @Nullable JvmProtoBuf.JvmMethodSignature syntheticMethod,
                @Nullable JvmProtoBuf.JvmMethodSignature getter,
                @Nullable JvmProtoBuf.JvmMethodSignature setter
        ) {
            JvmProtoBuf.JvmPropertySignature.Builder signature = JvmProtoBuf.JvmPropertySignature.newBuilder();

            if (fieldDesc != null) {
                assert fieldName != null : "Field name shouldn't be null when there's a field type: " + fieldDesc;
                signature.setField(fieldSignature(descriptor, fieldName, fieldDesc, isStaticInOuter));
            }

            if (syntheticMethod != null) {
                signature.setSyntheticMethod(syntheticMethod);
            }

            if (getter != null) {
                signature.setGetter(getter);
            }
            if (setter != null) {
                signature.setSetter(setter);
            }

            return signature.build();
        }

        @NotNull
        public JvmProtoBuf.JvmFieldSignature fieldSignature(
                @NotNull PropertyDescriptor descriptor,
                @NotNull String name,
                @NotNull String desc,
                boolean isStaticInOuter
        ) {
            JvmProtoBuf.JvmFieldSignature.Builder builder = JvmProtoBuf.JvmFieldSignature.newBuilder();
            if (!descriptor.getName().asString().equals(name)) {
                builder.setName(stringTable.getStringIndex(name));
            }
            if (requiresSignature(descriptor, desc)) {
                builder.setDesc(stringTable.getStringIndex(desc));
            }
            if (isStaticInOuter) {
                builder.setIsStaticInOuter(true);
            }
            return builder.build();
        }
    }
}
