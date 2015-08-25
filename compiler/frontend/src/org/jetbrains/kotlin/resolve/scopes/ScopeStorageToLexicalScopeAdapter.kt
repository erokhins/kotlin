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

import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.name.Name

public interface ScopeStorageToLexicalScopeAdapter: WritableScopeStorage, LexicalScope {
    // must be protected, but KT-3029 Protected does not work in traits: IllegalAccessError
    val descriptorLimit: Int
        get() = addedDescriptors.size()

    override fun getDeclaredDescriptors(): Collection<DeclarationDescriptor> = addedDescriptors.subList(0, descriptorLimit)

    override fun getDeclaredClassifier(name: Name, location: LookupLocation)
            = variableOrClassDescriptorByName(name, descriptorLimit) as? ClassifierDescriptor

    override fun getDeclaredVariables(name: Name, location: LookupLocation): Collection<VariableDescriptor>
            = listOfNotNull(variableOrClassDescriptorByName(name, descriptorLimit) as? VariableDescriptor)

    override fun getDeclaredFunctions(name: Name, location: LookupLocation): Collection<FunctionDescriptor>
            = functionsByName(name, descriptorLimit) ?: emptyList()
}