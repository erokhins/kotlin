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

package org.jetbrains.kotlin.resolve.lazy

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.JetImportDirective
import org.jetbrains.kotlin.resolve.scopes.ChainedScope
import org.jetbrains.kotlin.resolve.scopes.FileScope
import org.jetbrains.kotlin.resolve.scopes.JetScope

class LazyFileScope(
        scopeChain: List<JetScope>,
        private val aliasImportResolver: LazyImportResolver,
        private val allUnderImportResolver: LazyImportResolver,
        containingDeclaration: PackageFragmentDescriptor,
        debugName: String
) : ChainedScope(containingDeclaration, debugName, *scopeChain.toTypedArray()), FileScope {
    override val containingDeclaration_: DeclarationDescriptor
        get() = getContainingDeclaration()

    override fun getOwnClassifier(name: Name, location: LookupLocation) = getClassifier(name, location)

    override fun getOwnProperties(name: Name, location: LookupLocation) = getProperties(name, location)

    override fun getOwnFunctions(name: Name, location: LookupLocation) = getFunctions(name, location)

    public fun forceResolveAllImports() {
        aliasImportResolver.forceResolveAllContents()
        allUnderImportResolver.forceResolveAllContents()
    }

    public fun forceResolveImport(importDirective: JetImportDirective) {
        if (importDirective.isAllUnder()) {
            allUnderImportResolver.forceResolveImportDirective(importDirective)
        }
        else {
            aliasImportResolver.forceResolveImportDirective(importDirective)
        }
    }
}
