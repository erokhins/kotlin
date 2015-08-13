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

import com.intellij.util.SmartList
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.JetType
import org.jetbrains.kotlin.utils.Printer
import org.jetbrains.kotlin.utils.addIfNotNull

public fun LexicalScopePart.getFileScope(): FileScope {
    var currentScope = this
    while(currentScope.parentScope != null) {
        currentScope = currentScope.parentScope!!
    }
    assert(currentScope is FileScope) {
        "Not FileScope without parent: $currentScope" // todo improve debug message
    }
    return currentScope as FileScope
}

/**
 * Adds receivers to the list in order of locality, so that the closest (the most local) receiver goes first
 */
public fun LexicalScopePart.getImplicitReceiversHierarchy(): List<ReceiverParameterDescriptor> = collectFromMeAndParent { it.ownImplicitReceiver }

public fun LexicalScopePart.getDeclarationsByLabel(labelName: Name): Collection<DeclarationDescriptor> = collectFromMeAndParent {
    if (it.containingDeclarationAccessibleByLabel && it.containingDeclaration_.name == labelName) {
        it.containingDeclaration_
    } else {
        null
    }
}

@deprecated("Use getOwnProperties instead")
public fun LexicalScopePart.getLocalVariable(name: Name, location: LookupLocation = LookupLocation.NO_LOCATION): VariableDescriptor? {
    processForMeAndParent {
        if (it !is FileScope) { // todo check this
            it.getOwnProperties(name, location).singleOrNull()?.let { return it }
        }
    }
    return null
}

public fun LexicalScopePart.getClassifier(name: Name, location: LookupLocation = LookupLocation.NO_LOCATION): ClassifierDescriptor? {
    processForMeAndParent {
        it.getOwnClassifier(name, location)?.let { return it }
    }
    return null
}

public fun LexicalScopePart.asJetScope(): JetScope = LexicalToJetScopeAdapter(this)

private class LexicalToJetScopeAdapter(val lexicalScopePart: LexicalScopePart): JetScope {

    override fun getClassifier(name: Name, location: LookupLocation) = lexicalScopePart.getClassifier(name, location)

    override fun getPackage(name: Name) = lexicalScopePart.getFileScope().getPackage(name)

    override fun getProperties(name: Name, location: LookupLocation) = lexicalScopePart.collectAllFromMeAndParent {
        it.getOwnProperties(name, location)
    }

    override fun getLocalVariable(name: Name) = lexicalScopePart.getLocalVariable(name)

    override fun getFunctions(name: Name, location: LookupLocation) = lexicalScopePart.collectAllFromMeAndParent {
        it.getOwnFunctions(name, location)
    }


    override fun getSyntheticExtensionProperties(receiverTypes: Collection<JetType>, name: Name, location: LookupLocation)
            = lexicalScopePart.getFileScope().getSyntheticExtensionProperties(receiverTypes, name, location)

    override fun getSyntheticExtensionFunctions(receiverTypes: Collection<JetType>, name: Name, location: LookupLocation)
            = lexicalScopePart.getFileScope().getSyntheticExtensionFunctions(receiverTypes, name, location)

    override fun getSyntheticExtensionProperties(receiverTypes: Collection<JetType>)
        = lexicalScopePart.getFileScope().getSyntheticExtensionProperties(receiverTypes)

    override fun getSyntheticExtensionFunctions(receiverTypes: Collection<JetType>)
        = lexicalScopePart.getFileScope().getSyntheticExtensionFunctions(receiverTypes)

    override fun getContainingDeclaration() = lexicalScopePart.containingDeclaration_

    override fun getDeclarationsByLabel(labelName: Name) = lexicalScopePart.getDeclarationsByLabel(labelName)

    override fun getDescriptors(kindFilter: DescriptorKindFilter, nameFilter: (Name) -> Boolean) = lexicalScopePart.collectAllFromMeAndParent {
        if (it is FileScope) {
            it.getDescriptors(kindFilter, nameFilter)
        } else it.getOwnDeclaredDescriptors()
    }

    override fun getImplicitReceiversHierarchy() = lexicalScopePart.getImplicitReceiversHierarchy()
    override fun printScopeStructure(p: Printer) = lexicalScopePart.printScopeStructure(p)
    override fun getOwnDeclaredDescriptors() = lexicalScopePart.getOwnDeclaredDescriptors()
}

private inline fun LexicalScopePart.processForMeAndParent(process: (LexicalScopePart) -> Unit) {
    var currentScope = this
    process(currentScope)

    while(currentScope.parentScope != null) {
        currentScope = currentScope.parentScope!!
        process(currentScope)
    }
}

private inline fun <T: Any> LexicalScopePart.collectFromMeAndParent(
        toCollection: MutableList<T> = SmartList(),
        collect: (LexicalScopePart) -> T?
): List<T> {
    processForMeAndParent { toCollection.addIfNotNull(collect(it)) }
    return toCollection
}

private inline fun <T: Any> LexicalScopePart.collectAllFromMeAndParent(
        toCollection: MutableCollection<T> = SmartList(),
        collect: (LexicalScopePart) -> Collection<T>
): MutableCollection<T> {
    processForMeAndParent { toCollection.addAll(collect(it)) }
    return toCollection
}