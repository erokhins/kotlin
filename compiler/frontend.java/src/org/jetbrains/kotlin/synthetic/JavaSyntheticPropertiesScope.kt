/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.synthetic

import com.intellij.util.SmartList
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.PropertyDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertyGetterDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertySetterDescriptorImpl
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.load.java.propertyNameByGetMethodName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.SyntheticScope
import org.jetbrains.kotlin.resolve.scopes.SyntheticScopes
import org.jetbrains.kotlin.resolve.scopes.collectSyntheticExtensionProperties
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeSubstitutor
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf
import org.jetbrains.kotlin.types.typeUtil.isUnit
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeFirstWord
import org.jetbrains.kotlin.utils.addIfNotNull
import java.util.*

interface SyntheticJavaPropertyDescriptor : PropertyDescriptor {
    val getMethod: FunctionDescriptor
    val setMethod: FunctionDescriptor?

    companion object {
        fun findByGetterOrSetter(getterOrSetter: FunctionDescriptor, syntheticScopes: SyntheticScopes): SyntheticJavaPropertyDescriptor? {
            val name = getterOrSetter.name
            if (name.isSpecial) return null
            val identifier = name.identifier
            if (!identifier.startsWith("get") && !identifier.startsWith("is") && !identifier.startsWith("set")) return null // optimization

            val owner = getterOrSetter.containingDeclaration as? ClassDescriptor ?: return null

            val originalGetterOrSetter = getterOrSetter.original
            return syntheticScopes.collectSyntheticExtensionProperties(listOf(owner.defaultType))
                    .filterIsInstance<SyntheticJavaPropertyDescriptor>()
                    .firstOrNull { originalGetterOrSetter == it.getMethod || originalGetterOrSetter == it.setMethod }
        }

        fun findByGetterOrSetter(getterOrSetter: FunctionDescriptor, syntheticScope: SyntheticScope)
                = findByGetterOrSetter(getterOrSetter, object : SyntheticScopes {
            override val scopes: Collection<SyntheticScope> = listOf(syntheticScope)
        })

        fun propertyNameByGetMethodName(methodName: Name): Name?
                = org.jetbrains.kotlin.load.java.propertyNameByGetMethodName(methodName)

        fun propertyNameBySetMethodName(methodName: Name, withIsPrefix: Boolean): Name?
                = org.jetbrains.kotlin.load.java.propertyNameBySetMethodName(methodName, withIsPrefix)
    }
}

class JavaSyntheticPropertiesScope(storageManager: StorageManager, private val lookupTracker: LookupTracker) : SyntheticScope {
    private val syntheticPropertyByOriginalGet = storageManager.createMemoizedFunctionWithNullableValues<SimpleFunctionDescriptor, PropertyDescriptor> { originalGet ->
        syntheticPropertyByGetNotCached(originalGet, NoLookupLocation.FROM_SYNTHETIC_SCOPE)
    }

    private fun getSyntheticPropertyAndRecordLookups(kotlinType: KotlinType, name: Name, location: LookupLocation): PropertyDescriptor? {
        val getMethod = findGetMethod(kotlinType, name, location) ?: return null

        if (getMethod.original === getMethod) {
            // TODO: optimize
            kotlinType.memberScope.getContributedFunctions(setMethodName(getMethod.name), location)

            return  syntheticPropertyByOriginalGet(getMethod)
        }
        else {
            return syntheticPropertyByGetNotCached(getMethod, location)
        }
    }

    private fun findGetMethod(kotlinType: KotlinType, name: Name, location: LookupLocation): SimpleFunctionDescriptor? {
        if (name.isSpecial) return null

        val identifier = name.identifier
        if (identifier.isEmpty()) return null

        val firstChar = identifier[0]
        if (!firstChar.isJavaIdentifierStart() || firstChar in 'A'..'Z') return null

        val memberScope = kotlinType.memberScope

        val possibleGetMethodNames = possibleGetMethodNames(name)
        return possibleGetMethodNames
                                .flatMap { memberScope.getContributedFunctions(it, location) }
                                .singleOrNull {
                                    isGoodGetMethod(it) && it.original.hasJavaOriginInHierarchy()
                                }
    }

    private fun syntheticPropertyByGetNotCached(getMethod: SimpleFunctionDescriptor, location: LookupLocation): PropertyDescriptor? {
        val memberScope = getMethod.dispatchReceiverParameter!!.value.type.memberScope

        val setMethodName = setMethodName(getMethod.name)
        val setMethod = memberScope.getContributedFunctions(setMethodName, location)
                .singleOrNull { isGoodSetMethod(it, getMethod) }

        val propertyType = getMethod.returnType!!

        return MyPropertyDescriptor.create(getMethod, setMethod, propertyNameByGetMethodName(getMethod.name)!!, propertyType)
    }

    private fun isGoodGetMethod(descriptor: FunctionDescriptor): Boolean {
        val returnType = descriptor.returnType ?: return false
        if (returnType.isUnit()) return false

        return descriptor.valueParameters.isEmpty()
               && descriptor.dispatchReceiverParameter != null
               && descriptor.typeParameters.isEmpty()
               && descriptor.visibility.isVisibleOutside()
    }

    private fun isGoodSetMethod(descriptor: FunctionDescriptor, getMethod: FunctionDescriptor): Boolean {
        val propertyType = getMethod.returnType ?: return false
        val parameter = descriptor.valueParameters.singleOrNull() ?: return false
        if (!TypeUtils.equalTypes(parameter.type, propertyType)) {
            if (!propertyType.isSubtypeOf(parameter.type)) return false
            if (descriptor.findOverridden {
                val baseProperty = SyntheticJavaPropertyDescriptor.findByGetterOrSetter(it, this)
                baseProperty?.getMethod?.name == getMethod.name
            } == null) return false
        }

        return parameter.varargElementType == null
               && descriptor.typeParameters.isEmpty()
               && descriptor.visibility.isVisibleOutside()
    }

    private fun FunctionDescriptor.findOverridden(condition: (FunctionDescriptor) -> Boolean): FunctionDescriptor? {
        for (descriptor in overriddenDescriptors) {
            if (condition(descriptor)) return descriptor
            descriptor.findOverridden(condition)?.let { return it }
        }
        return null
    }

    override fun getSyntheticExtensionProperties(receiverTypes: Collection<KotlinType>, name: Name, location: LookupLocation): Collection<PropertyDescriptor> {
        var result: SmartList<PropertyDescriptor>? = null
        for (type in receiverTypes) {
            result = result.add(getSyntheticPropertyAndRecordLookups(type, name, location))
        }
        return when {
            result == null -> emptyList()
            result.size > 1 -> result.toSet()
            else -> result
        }
    }

    override fun getSyntheticExtensionProperties(receiverTypes: Collection<KotlinType>): Collection<PropertyDescriptor> {
        val result = ArrayList<PropertyDescriptor>()
        receiverTypes.forEach { result.collectSyntheticProperties(it) }
        return result
    }

    private fun MutableList<PropertyDescriptor>.collectSyntheticProperties(type: KotlinType) {
        for (descriptor in type.memberScope.getContributedDescriptors(DescriptorKindFilter.FUNCTIONS)) {
            if (descriptor is FunctionDescriptor) {
                val propertyName = SyntheticJavaPropertyDescriptor.propertyNameByGetMethodName(descriptor.getName()) ?: continue
                addIfNotNull(getSyntheticPropertyAndRecordLookups(type, propertyName, NoLookupLocation.FROM_SYNTHETIC_SCOPE))
            }
        }
    }

    private fun SmartList<PropertyDescriptor>?.add(property: PropertyDescriptor?): SmartList<PropertyDescriptor>? {
        if (property == null) return this
        val list = if (this != null) this else SmartList()
        list.add(property)
        return list
    }

    //TODO: reuse code with generation?

    private fun possibleGetMethodNames(propertyName: Name): List<Name> {
        val result = ArrayList<Name>(3)
        val identifier = propertyName.identifier

        if (JvmAbi.startsWithIsPrefix(identifier)) {
            result.add(propertyName)
        }

        val capitalize1 = identifier.capitalizeAsciiOnly()
        val capitalize2 = identifier.capitalizeFirstWord(asciiOnly = true)
        result.add(Name.identifier("get" + capitalize1))
        if (capitalize2 != capitalize1) {
            result.add(Name.identifier("get" + capitalize2))
        }

        return result
                .filter { SyntheticJavaPropertyDescriptor.propertyNameByGetMethodName(it) == propertyName } // don't accept "uRL" for "getURL" etc
    }

    private fun setMethodName(getMethodName: Name): Name {
        val identifier = getMethodName.identifier
        val prefix = when {
            identifier.startsWith("get") -> "get"
            identifier.startsWith("is") -> "is"
            else -> throw IllegalArgumentException()
        }
        return Name.identifier("set" + identifier.removePrefix(prefix))
    }

    override fun getSyntheticExtensionFunctions(receiverTypes: Collection<KotlinType>, name: Name, location: LookupLocation): Collection<FunctionDescriptor> = emptyList()
    override fun getSyntheticExtensionFunctions(receiverTypes: Collection<KotlinType>): Collection<FunctionDescriptor> = emptyList()

    private class MyPropertyDescriptor(
            containingDeclaration: DeclarationDescriptor,
            original: PropertyDescriptor?,
            annotations: Annotations,
            modality: Modality,
            visibility: Visibility,
            isVar: Boolean,
            name: Name,
            kind: CallableMemberDescriptor.Kind,
            getMethod: FunctionDescriptor
    ) : SyntheticJavaPropertyDescriptor, PropertyDescriptorImpl(containingDeclaration, original, annotations,
                                                                modality, visibility, isVar, name, kind, getMethod.source,
                                                                /* lateInit = */ false, /* isConst = */ false) {

        override var getMethod: FunctionDescriptor = getMethod
            private set

        override var setMethod: FunctionDescriptor? = null
            private set

        companion object {
            fun create(getMethod: FunctionDescriptor, setMethod: FunctionDescriptor?, name: Name, type: KotlinType): MyPropertyDescriptor {
                val visibility = syntheticExtensionVisibility(getMethod)
                val descriptor = MyPropertyDescriptor(getMethod.containingDeclaration,
                                                      null,
                                                      Annotations.EMPTY,
                                                      getMethod.modality,
                                                      visibility,
                                                      setMethod != null,
                                                      name,
                                                      CallableMemberDescriptor.Kind.SYNTHESIZED,
                                                      getMethod)
                descriptor.getMethod = getMethod
                descriptor.setMethod = setMethod

                descriptor.setType(type, emptyList(), getMethod.dispatchReceiverParameter!!, null as KotlinType?)

                val getter = PropertyGetterDescriptorImpl(descriptor,
                                                          getMethod.annotations,
                                                          Modality.FINAL,
                                                          visibility,
                                                          false,
                                                          getMethod.isExternal,
                                                          CallableMemberDescriptor.Kind.SYNTHESIZED,
                                                          null,
                                                          getMethod.source)
                getter.initialize(null)

                val setter = if (setMethod != null)
                    PropertySetterDescriptorImpl(descriptor,
                                                 setMethod.annotations,
                                                 Modality.FINAL,
                                                 syntheticExtensionVisibility(setMethod),
                                                 false,
                                                 setMethod.isExternal,
                                                 CallableMemberDescriptor.Kind.SYNTHESIZED,
                                                 null,
                                                 setMethod.source)
                else
                    null
                setter?.initializeDefault()

                descriptor.initialize(getter, setter)

                return descriptor
            }
        }

        override fun createSubstitutedCopy(newOwner: DeclarationDescriptor, newModality: Modality, newVisibility: Visibility, original: PropertyDescriptor?, kind: CallableMemberDescriptor.Kind): PropertyDescriptorImpl {
            return MyPropertyDescriptor(newOwner, this, annotations, newModality, newVisibility, isVar, name, kind, getMethod).apply {
                setMethod = this@MyPropertyDescriptor.setMethod
            }
        }

        override fun substitute(originalSubstitutor: TypeSubstitutor): PropertyDescriptor? {
            val descriptor = super.substitute(originalSubstitutor) as MyPropertyDescriptor
            if (descriptor == this) return descriptor

            descriptor.getMethod = getMethod.substitute(originalSubstitutor)
            descriptor.setMethod = setMethod?.substitute(originalSubstitutor)
            return descriptor
        }
    }
}
