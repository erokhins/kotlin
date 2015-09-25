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

@file:JvmName("ScopeUtils")

package org.jetbrains.kotlin.idea.util

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ClassDescriptorWithResolutionScopes
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.diagnostics.DiagnosticUtils
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.JetClassBody
import org.jetbrains.kotlin.psi.JetElement
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.scopes.JetScope
import org.jetbrains.kotlin.resolve.scopes.LexicalScope


public fun JetScope.getAllAccessibleVariables(name: Name): Collection<VariableDescriptor>
        = getVariablesFromImplicitReceivers(name) + getProperties(name, NoLookupLocation.FROM_IDE) + listOfNotNull(getLocalVariable(name))

public fun JetScope.getAllAccessibleFunctions(name: Name): Collection<FunctionDescriptor>
        = getImplicitReceiversWithInstance().flatMap { it.type.memberScope.getFunctions(name, NoLookupLocation.FROM_IDE) } +
          getFunctions(name, NoLookupLocation.FROM_IDE)

public fun JetScope.getVariablesFromImplicitReceivers(name: Name): Collection<VariableDescriptor> = getImplicitReceiversWithInstance().flatMap {
    it.type.memberScope.getProperties(name, NoLookupLocation.FROM_IDE)
}

public fun JetScope.getVariableFromImplicitReceivers(name: Name): VariableDescriptor? {
    getImplicitReceiversWithInstance().forEach {
        it.type.memberScope.getProperties(name, NoLookupLocation.FROM_IDE).singleOrNull()?.let { return it }
    }
    return null
}

public fun PsiElement.getLexicalScope(bindingContext: BindingContext): LexicalScope {
    for (parent in parentsWithSelf) {
        if (parent is JetElement) {
            bindingContext[BindingContext.LEXICAL_SCOPE, parent]?.let { return it }
        }

        if (parent is JetClassBody) {
            val classDescriptor = bindingContext[BindingContext.CLASS, parent.getParent()] as? ClassDescriptorWithResolutionScopes
            if (classDescriptor != null) {
                return classDescriptor.scopeForMemberDeclarationResolution
            }
        }
    }

    throw IllegalStateException("Cannot find LEXICAL_SCOPE for element: $this at ${DiagnosticUtils.atLocation(this)}")
}