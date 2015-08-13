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

package org.jetbrains.kotlin.resolve.scopes

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.JetType
import org.jetbrains.kotlin.utils.Printer

// see ScopeUtils.kt in the frontend module

public interface LexicalScopePart {
    public val parentScope: LexicalScopePart?

    // TODO: rename after removing JetScope
    public val containingDeclaration_: DeclarationDescriptor
    public val containingDeclarationAccessibleByLabel: Boolean

    public val ownImplicitReceiver: ReceiverParameterDescriptor?

    // this method throws UOE for FileScope
    public fun getOwnDeclaredDescriptors(): Collection<DeclarationDescriptor>

    public fun getOwnClassifier(name: Name, location: LookupLocation = LookupLocation.NO_LOCATION): ClassifierDescriptor?

    // need collection here because there may be extension property foo and usual property foo
    public fun getOwnProperties(name: Name, location: LookupLocation = LookupLocation.NO_LOCATION): Collection<VariableDescriptor>
    public fun getOwnFunctions(name: Name, location: LookupLocation = LookupLocation.NO_LOCATION): Collection<FunctionDescriptor>

    public fun printScopeStructure(p: Printer)
}

public interface FileScope: LexicalScopePart {
    override val parentScope: LexicalScopePart?
        get() = null

    override val containingDeclarationAccessibleByLabel: Boolean
        get() = false

    override val ownImplicitReceiver: ReceiverParameterDescriptor?
        get() = null

    // methods getOwnSmth for this scope will be delegated to importScope

    fun getPackage(name: Name): PackageViewDescriptor?

    public fun getSyntheticExtensionProperties(receiverTypes: Collection<JetType>, name: Name, location: LookupLocation = LookupLocation.NO_LOCATION): Collection<PropertyDescriptor>
    public fun getSyntheticExtensionFunctions(receiverTypes: Collection<JetType>, name: Name, location: LookupLocation = LookupLocation.NO_LOCATION): Collection<FunctionDescriptor>

    public fun getSyntheticExtensionProperties(receiverTypes: Collection<JetType>): Collection<PropertyDescriptor>
    public fun getSyntheticExtensionFunctions(receiverTypes: Collection<JetType>): Collection<FunctionDescriptor>

    public fun getDescriptors(
            kindFilter: DescriptorKindFilter = DescriptorKindFilter.ALL,
            nameFilter: (Name) -> Boolean = JetScope.ALL_NAME_FILTER
    ): Collection<DeclarationDescriptor>
}
