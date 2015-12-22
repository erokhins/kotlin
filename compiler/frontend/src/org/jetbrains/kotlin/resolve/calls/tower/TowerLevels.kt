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

import com.intellij.util.SmartList
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.calls.util.FakeCallableDescriptorForObject
import org.jetbrains.kotlin.resolve.descriptorUtil.hasClassValueDescriptor
import org.jetbrains.kotlin.resolve.descriptorUtil.hasLowPriorityInOverloadResolution
import org.jetbrains.kotlin.resolve.scopes.ResolutionScope
import org.jetbrains.kotlin.resolve.scopes.SyntheticScopes
import org.jetbrains.kotlin.resolve.scopes.collectSyntheticExtensionFunctions
import org.jetbrains.kotlin.resolve.scopes.collectSyntheticExtensionProperties
import org.jetbrains.kotlin.resolve.scopes.receivers.CastImplicitClassReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitClassReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.QualifierReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.resolve.selectMostSpecificInEachOverridableGroup
import org.jetbrains.kotlin.types.ErrorUtils
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isDynamic
import org.jetbrains.kotlin.types.typeUtil.getImmediateSuperclassNotAny
import java.util.*

internal abstract class AbstractScopeTowerLevel(
        protected val scopeTower: ResolutionScopeContext
): ScopeTowerLevel {
    protected val location: LookupLocation get() = scopeTower.location

    protected fun <D : CallableDescriptor> createCandidateDescriptor(
            descriptor: D,
            dispatchReceiver: ReceiverValue?,
            extensionReceiver: ReceiverValue?,
            explicitReceiverKind: ExplicitReceiverKind,
            diagnostics: List<ResolutionDiagnostic> = emptyList()
    ): CandidateWithMatchedReceivers<D>? {
        if ((descriptor.extensionReceiverParameter == null) != (extensionReceiver == null)) return null

        val allDiagnostics = SmartList<ResolutionDiagnostic>(diagnostics)

        if (ErrorUtils.isError(descriptor)) {
            allDiagnostics.add(ErrorDescriptorDiagnostic)
        }
        else {
            if ((descriptor.dispatchReceiverParameter == null) != (dispatchReceiver == null)) return null
            if (descriptor.hasLowPriorityInOverloadResolution()) allDiagnostics.add(LowPriorityDescriptorDiagnostic)
            if (descriptor.isSynthesized) allDiagnostics.add(SynthesizedDescriptorDiagnostic)

            Visibilities.findInvisibleMember(
                    dispatchReceiver, descriptor,
                    scopeTower.lexicalScope.ownerDescriptor
            )?.let { allDiagnostics.add(VisibilityError(it)) }
        }
        return CandidateWithMatchedReceiversImpl(dispatchReceiver, extensionReceiver, explicitReceiverKind, descriptor, allDiagnostics)
    }

}

// todo KT-9538 Unresolved inner class via subclass reference
// todo add static methods & fields with error
internal class ReceiverScopeTowerLevel(
        scopeTower: ResolutionScopeContext,
        val dispatchReceiver: ReceiverValue
): AbstractScopeTowerLevel(scopeTower) {

    private fun <D : CallableDescriptor> collectMembers(
            extensionReceiver: ReceiverValue?,
            receiverKind: ExplicitReceiverKind,
            getDescriptors: ResolutionScope.(KotlinType?) -> Collection<D> // members & synthesized members + possible extensions for passed type
    ): Collection<CandidateWithMatchedReceivers<D>> {
        val result = ArrayList<CandidateWithMatchedReceivers<D>>(0)

        fun MutableList<CandidateWithMatchedReceivers<D>>.addCandidatesForType(type: KotlinType, dispatchReceiver: ReceiverValue, diagnostics: List<ResolutionDiagnostic>) {
            type.memberScope.getDescriptors(type).mapNotNullTo(this) { descriptor ->
                // descriptor is member or synthesized member
                if (descriptor.dispatchReceiverParameter != null || ErrorUtils.isError(descriptor)) {
                    return@mapNotNullTo createCandidateDescriptor(descriptor, dispatchReceiver, extensionReceiver, receiverKind, diagnostics)
                }

                if (extensionReceiver != null) return@mapNotNullTo null
                assert(descriptor.extensionReceiverParameter != null) {
                    "Unexpected descriptor without both receiver parameters: $descriptor."
                }
                val realReceiverKind = when (receiverKind) {
                    ExplicitReceiverKind.NO_EXPLICIT_RECEIVER -> ExplicitReceiverKind.NO_EXPLICIT_RECEIVER
                    ExplicitReceiverKind.DISPATCH_RECEIVER -> ExplicitReceiverKind.EXTENSION_RECEIVER
                    else -> throw IllegalStateException("Unexpected receiver kind $receiverKind with synthesized descriptor: $descriptor")
                }
                createCandidateDescriptor(descriptor, null, dispatchReceiver, realReceiverKind)
            }
        }

        result.addCandidatesForType(dispatchReceiver.type, dispatchReceiver, listOf())

        val smartCastPossibleTypes = scopeTower.dataFlowInfo.getSmartCastTypes(dispatchReceiver)
        val unstableError = if (scopeTower.dataFlowInfo.isStableReceiver(dispatchReceiver)) null else UnstableSmartCastDiagnostic
        val unstableCandidates = if (unstableError != null) ArrayList<CandidateWithMatchedReceivers<D>>(0) else null

        for (possibleType in smartCastPossibleTypes) {
            (unstableCandidates ?: result).addCandidatesForType(possibleType, dispatchReceiver.smartCastReceiver(possibleType),
                                                                listOfNotNull(unstableError, UsedSmartCastForDispatchReceiver(possibleType)))
        }

        if (smartCastPossibleTypes.isNotEmpty()) {
            if (unstableCandidates == null) {
                result.retainAll(result.selectMostSpecificInEachOverridableGroup { descriptor })
            }
            else {
                result.addAll(unstableCandidates.selectMostSpecificInEachOverridableGroup { descriptor })
            }
        }

        if (dispatchReceiver.type.isDynamic()) {
            scopeTower.dynamicScope.getDescriptors(null).mapNotNullTo(result) {
                createCandidateDescriptor(it, dispatchReceiver, extensionReceiver, receiverKind, listOf(DynamicDescriptorDiagnostic))
            }
        }

        return result
    }

    private fun ReceiverValue.smartCastReceiver(targetType: KotlinType)
            = if (this is ImplicitClassReceiver) CastImplicitClassReceiver(this.classDescriptor, targetType) else this

    override fun getVariables(name: Name, extensionReceiver: ReceiverValue?, receiverKind: ExplicitReceiverKind): Collection<CandidateWithMatchedReceivers<VariableDescriptor>> {
        return collectMembers(extensionReceiver, receiverKind) {
            getContributedVariables(name, location) + scopeTower.syntheticScopes.scopes.flatMap { it.getSyntheticExtensionProperties(this, name, location) }
        }
    }

    override fun getFunctions(name: Name, extensionReceiver: ReceiverValue?, receiverKind: ExplicitReceiverKind): Collection<CandidateWithMatchedReceivers<FunctionDescriptor>> {
        return collectMembers(extensionReceiver, receiverKind) {
            getContributedFunctions(name, location) + it.getInnerConstructors(name, location) + scopeTower.syntheticScopes.scopes.flatMap { it.getSyntheticExtensionFunctions(this, name, location) }
        }
    }
}

internal class QualifierScopeTowerLevel(scopeTower: ResolutionScopeContext, val qualifier: QualifierReceiver) : AbstractScopeTowerLevel(scopeTower) {
    override fun getVariables(name: Name, extensionReceiver: ReceiverValue?, receiverKind: ExplicitReceiverKind) = qualifier.getNestedClassesAndPackageMembersScope()
            .getContributedVariablesAndObjects(name, location).mapNotNull {
                createCandidateDescriptor(it, null, extensionReceiver, receiverKind)
            }

    override fun getFunctions(name: Name, extensionReceiver: ReceiverValue?, receiverKind: ExplicitReceiverKind) = qualifier.getNestedClassesAndPackageMembersScope()
            .getContributedFunctionsAndConstructors(name, location).mapNotNull {
                createCandidateDescriptor(it, null, extensionReceiver, receiverKind)
            }
}

// KT-3335 Creating imported super class' inner class fails in codegen
internal open class ScopeBasedTowerLevel(
        scopeTower: ResolutionScopeContext,
        private val resolutionScope: ResolutionScope
) : AbstractScopeTowerLevel(scopeTower) {

    override fun getVariables(name: Name, extensionReceiver: ReceiverValue?, receiverKind: ExplicitReceiverKind): Collection<CandidateWithMatchedReceivers<VariableDescriptor>>
            = resolutionScope.getContributedVariablesAndObjects(name, location).mapNotNull {
                createCandidateDescriptor(it, null, extensionReceiver, receiverKind)
            }

    override fun getFunctions(name: Name, extensionReceiver: ReceiverValue?, receiverKind: ExplicitReceiverKind): Collection<CandidateWithMatchedReceivers<FunctionDescriptor>>
            = resolutionScope.getContributedFunctionsAndConstructors(name, location).mapNotNull {
                createCandidateDescriptor(it, null, extensionReceiver, receiverKind)
            }
}

private fun KotlinType.getClassifierFromMeAndSuperclasses(name: Name, location: LookupLocation): ClassifierDescriptor? {
    var superclass: KotlinType? = this
    while (superclass != null) {
        superclass.memberScope.getContributedClassifier(name, location)?.let { return it }
        superclass = superclass.getImmediateSuperclassNotAny()
    }
    return null
}

private fun KotlinType?.getInnerConstructors(name: Name, location: LookupLocation): Collection<FunctionDescriptor> {
    val classifierDescriptor = getClassWithConstructors(this?.getClassifierFromMeAndSuperclasses(name, location))
    return classifierDescriptor?.constructors?.filter { it.dispatchReceiverParameter != null } ?: emptyList()
}

private fun ResolutionScope.getContributedFunctionsAndConstructors(name: Name, location: LookupLocation): Collection<FunctionDescriptor> {
    val classWithConstructors = getClassWithConstructors(getContributedClassifier(name, location))
    return getContributedFunctions(name, location) +
           (classWithConstructors?.constructors?.filter { it.dispatchReceiverParameter == null } ?: emptyList())
}

private fun ResolutionScope.getContributedVariablesAndObjects(name: Name, location: LookupLocation): Collection<VariableDescriptor> {
    val objectDescriptor = getFakeDescriptorForObject(getContributedClassifier(name, location))

    return getContributedVariables(name, location) + listOfNotNull(objectDescriptor)
}


private fun getFakeDescriptorForObject(classifier: ClassifierDescriptor?): FakeCallableDescriptorForObject? {
    if (classifier !is ClassDescriptor || !classifier.hasClassValueDescriptor) return null // todo

    return FakeCallableDescriptorForObject(classifier)
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