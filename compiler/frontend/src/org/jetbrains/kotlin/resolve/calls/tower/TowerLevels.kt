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

package org.jetbrains.kotlin.resolve.calls.tower

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.calls.util.FakeCallableDescriptorForObject
import org.jetbrains.kotlin.resolve.descriptorUtil.hasClassObjectType
import org.jetbrains.kotlin.resolve.scopes.FileScope
import org.jetbrains.kotlin.resolve.scopes.JetScope
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.receivers.QualifierReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.resolve.scopes.utils.getClassifier
import org.jetbrains.kotlin.types.ErrorUtils
import org.jetbrains.kotlin.types.JetType

public interface TowerLevel {

    fun getVariables(name: Name): Collection<CandidateDescriptor<VariableDescriptor>>

    fun getFunctions(name: Name): Collection<CandidateDescriptor<FunctionDescriptor>>
}

internal abstract class AbstractTowerLevel(
        protected val resolveTower: ResolveTower
): TowerLevel {
    protected val location: LookupLocation get() = resolveTower.location

    protected fun <D : CallableDescriptor> createCandidateDescriptor(
            descriptor: D,
            dispatchReceiver: ReceiverValue?,
            specialError: ResolveCandidateError? = null,
            dispatchReceiverSmartCastType: JetType? = null
    ): CandidateDescriptor<D> {
        val visibilityError = checkVisibility(
                resolveTower.resolutionContext.scope.ownerDescriptor,
                descriptor,
                resolveTower.resolutionContext.trace.bindingContext,
                dispatchReceiver ?: ReceiverValue.NO_RECEIVER
        )
        return CandidateDescriptorImpl(dispatchReceiver, descriptor, listOfNotNull(specialError, visibilityError), dispatchReceiverSmartCastType)
    }

}

// todo create error for constructors call and for implicit receiver
// todo KT-9538 Unresolved inner class via subclass reference
// todo Future plan: move constructors and fake variables for objects to class member scope.
internal abstract class ConstructorsAndFakeVariableHackLevel(resolveTower: ResolveTower) : AbstractTowerLevel(resolveTower) {

    protected fun createConstructors(
            classifier: ClassifierDescriptor?,
            dispatchReceiver: ReceiverValue?,
            dispatchReceiverSmartCastType: JetType? = null,
            reportError: (ClassDescriptor) -> ResolveCandidateError? = { null }
    ): Collection<CandidateDescriptor<FunctionDescriptor>> {
        val classDescriptor = getClassWithConstructors(classifier) ?: return emptyList()

        val specialError = reportError(classDescriptor)
        return classDescriptor.constructors.map {

            val dispatchReceiverHack = if (dispatchReceiver == null && it.dispatchReceiverParameter != null) {
                it.dispatchReceiverParameter?.value // todo this is hack for Scope Level
            }
            else if (dispatchReceiver != null && it.dispatchReceiverParameter == null) { // should be reported error
                null
            }
            else {
                dispatchReceiver
            }

            createCandidateDescriptor(it, dispatchReceiverHack, specialError, dispatchReceiverSmartCastType)
        }
    }

    protected fun createVariableDescriptor(
            classifier: ClassifierDescriptor?,
            dispatchReceiverSmartCastType: JetType? = null,
            reportError: (ClassDescriptor) -> ResolveCandidateError? = { null }
    ): CandidateDescriptor<VariableDescriptor>? {
        val fakeVariable = getFakeDescriptorForObject(classifier) ?: return null
        return createCandidateDescriptor(fakeVariable, null, reportError(fakeVariable.classDescriptor), dispatchReceiverSmartCastType)
    }


    private fun getClassWithConstructors(classifier: ClassifierDescriptor?): ClassDescriptor? {
        if (classifier !is ClassDescriptor || ErrorUtils.isError(classifier)
            // Constructors of singletons shouldn't be callable from the code
            || classifier.kind.isSingleton) {
            return null
        }
        else {
            return classifier
        }
    }

    private fun getFakeDescriptorForObject(classifier: ClassifierDescriptor?): FakeCallableDescriptorForObject? {
        if (classifier !is ClassDescriptor || !classifier.hasClassObjectType) return null

        return FakeCallableDescriptorForObject(classifier)
    }
}

internal class ReceiverTowerLevel(
        resolveTower: ResolveTower,
        val dispatchReceiver: ReceiverValue
): ConstructorsAndFakeVariableHackLevel(resolveTower) {
    private val memberScope = dispatchReceiver.type.memberScope

    private fun <D: CallableDescriptor> collectMembers(
            members: JetScope.() -> Collection<D>,
            additionalDescriptors: JetScope.(smartCastType: JetType?) -> Collection<CandidateDescriptor<D>> // todo
    ): Collection<CandidateDescriptor<D>> {
        var result: Collection<CandidateDescriptor<D>> = memberScope.members().map {
            createCandidateDescriptor(it, dispatchReceiver)
        } fastPlus memberScope.additionalDescriptors(null)

        val smartCastPossibleTypes = resolveTower.smartCastCache.getSmartCastPossibleTypes(dispatchReceiver)
        val unstableError = if (resolveTower.smartCastCache.isStableReceiver(dispatchReceiver)) null else UnstableSmartCast()

        for (possibleType in smartCastPossibleTypes) {
            result = result fastPlus possibleType.memberScope.members().map {
                createCandidateDescriptor(it, dispatchReceiver, unstableError, dispatchReceiverSmartCastType = possibleType)
            }

            result = result fastPlus possibleType.memberScope.additionalDescriptors(possibleType).map {
                it.addError(unstableError)
            }
        }

        return result
    }

    // todo add static methods & fields with error
    override fun getVariables(name: Name): Collection<CandidateDescriptor<VariableDescriptor>> {
        return collectMembers({ getProperties(name, location) }) {
            smartCastType ->
            listOfNotNull(createVariableDescriptor(getClassifier(name, location), smartCastType){ NestedClassViaInstanceReference(it) } )
        }
    }

    override fun getFunctions(name: Name): Collection<CandidateDescriptor<FunctionDescriptor>> {
        return collectMembers({ getFunctions(name, location) }) {
            smartCastType ->
            createConstructors(getClassifier(name, location), dispatchReceiver, smartCastType) {
                if (it.isInner) null else NestedClassViaInstanceReference(it)
            }
        }
    }
}

internal class QualifierTowerLevel(resolveTower: ResolveTower, qualifier: QualifierReceiver) : ConstructorsAndFakeVariableHackLevel(resolveTower) {
    private val qualifierScope = qualifier.getNestedClassesAndPackageMembersScope()

    override fun getVariables(name: Name): Collection<CandidateDescriptor<VariableDescriptor>> {
        val variables = qualifierScope.getProperties(name, location).map {
            createCandidateDescriptor(it, dispatchReceiver = null)
        }
        return variables fastPlus createVariableDescriptor(qualifierScope.getClassifier(name, location))
    }

    override fun getFunctions(name: Name): Collection<CandidateDescriptor<FunctionDescriptor>> {
        val functions = qualifierScope.getFunctions(name, location).map {
            createCandidateDescriptor(it, dispatchReceiver = null)
        }
        val constructors = createConstructors(qualifierScope.getClassifier(name, location), dispatchReceiver = null) {
            if (it.isInner) InnerClassViaStaticReference(it) else null
        }
        return functions fastPlus constructors
    }
}

internal open class ScopeTowerLevel(
        resolveTower: ResolveTower,
        private val lexicalScope: LexicalScope
) : ConstructorsAndFakeVariableHackLevel(resolveTower) {

    override fun getVariables(name: Name): Collection<CandidateDescriptor<VariableDescriptor>> {
        val variables = lexicalScope.getDeclaredVariables(name, location).map {
            createCandidateDescriptor(it, dispatchReceiver = null)
        }
        return variables fastPlus createVariableDescriptor(lexicalScope.getClassifier(name, location))
    }

    override fun getFunctions(name: Name): Collection<CandidateDescriptor<FunctionDescriptor>> {
        val functions = lexicalScope.getDeclaredFunctions(name, location).map {
            createCandidateDescriptor(it, dispatchReceiver = null)
        }

        // todo report errors for constructors if there is no match receiver
        return functions fastPlus createConstructors(lexicalScope.getClassifier(name, location), dispatchReceiver = null) {
            if (!it.isInner) return@createConstructors null

            // todo add constructors functions to member class member scope
            // KT-3335 Creating imported super class' inner class fails in codegen
            UnsupportedInnerClassCall("Constructor call for inner class from subclass unsupported")
        }
    }
}

internal class FileScopeTowerLevel(
        resolveTower: ResolveTower,
        private val fileScope: FileScope
): ScopeTowerLevel(resolveTower, fileScope) {

    // before we creating this level all smartCasts for implicit receivers will be calculated
    private val allKnownReceivers = (resolveTower.implicitReceiversHierarchy.map { it.value } fastPlus resolveTower.explicitReceiver).flatMap {
        resolveTower.smartCastCache.getSmartCastPossibleTypes(it) fastPlus it.type
    }

    override fun getVariables(name: Name): Collection<CandidateDescriptor<VariableDescriptor>> {
        val synthetic = fileScope.getSyntheticExtensionProperties(allKnownReceivers, name, location).map {
            createCandidateDescriptor(it, dispatchReceiver = null)
        }
        return super.getVariables(name) fastPlus synthetic
    }

    override fun getFunctions(name: Name): Collection<CandidateDescriptor<FunctionDescriptor>> {
        val synthetic = fileScope.getSyntheticExtensionFunctions(allKnownReceivers, name, location).map {
            createCandidateDescriptor(it, dispatchReceiver = null)
        }
        return super.getFunctions(name) fastPlus synthetic
    }
}