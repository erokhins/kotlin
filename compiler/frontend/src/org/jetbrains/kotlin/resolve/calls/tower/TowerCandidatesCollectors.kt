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

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ReceiverParameterDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.calls.CallTransformer
import org.jetbrains.kotlin.resolve.calls.model.MutableResolvedCall
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.scopes.receivers.QualifierReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.utils.addToStdlib.check

// todo convert ResolvedCall to kotlin
internal interface TowerCandidatesCollector</*out */D : CallableDescriptor> {
    fun pushTowerLevel(level: TowerLevel)

    fun pushImplicitReceiver(implicitReceiver: ReceiverParameterDescriptor)

    // candidates with match receivers (dispatch was matched already in TowerLevel)
    fun getCurrentCandidates(): Collection<Pair<MutableResolvedCall<D>, ResolveCandidateStatus>>
}

internal class KnownResultCandidatesCollector<D: CallableDescriptor>(
        result: Collection<Pair<MutableResolvedCall<D>, ResolveCandidateStatus>>
): TowerCandidatesCollector<D> {
    var candidates = result

    override fun pushTowerLevel(level: TowerLevel) {
        candidates = emptyList()
    }

    override fun pushImplicitReceiver(implicitReceiver: ReceiverParameterDescriptor) {
        candidates = emptyList()
    }

    override fun getCurrentCandidates() = candidates
}

internal abstract class AbstractTowerCandidatesCollector<D : CallableDescriptor>(
        val context: OverloadTowerResolver.OverloadTowerResolverContext
) : TowerCandidatesCollector<D> {
    protected val name: Name = context.name

    fun checkCandidate(candidate: CandidateDescriptor<D>, explicitReceiverKind: ExplicitReceiverKind, extensionReceiver: ReceiverValue?)
            = context.overloadTowerResolver.checkCandidate(context, candidate, explicitReceiverKind, extensionReceiver)
}

internal class ExplicitReceiverTowerCandidateCollector<D: CallableDescriptor>(
        context: OverloadTowerResolver.OverloadTowerResolverContext,
        val explicitReceiver: ReceiverValue,
        val collectCandidates: TowerLevel.(Name) -> Collection<CandidateDescriptor<D>>
): AbstractTowerCandidatesCollector<D>(context) {
    private var currentCandidates: Collection<Pair<MutableResolvedCall<D>, ResolveCandidateStatus>> = resolveAsMember()

    override fun pushTowerLevel(level: TowerLevel) {
        currentCandidates = resolveAsExtension(level)
    }

    override fun pushImplicitReceiver(implicitReceiver: ReceiverParameterDescriptor) {
        // no candidates, because we already have receiver
        currentCandidates = emptyList()
    }

    override fun getCurrentCandidates() = currentCandidates

    private fun resolveAsMember(): Collection<Pair<MutableResolvedCall<D>, ResolveCandidateStatus>> {
        val members = ReceiverTowerLevel(context.resolveTower, explicitReceiver).collectCandidates(name).filter { !it.requiredExtensionParameter }
        return members.map { checkCandidate(it, ExplicitReceiverKind.DISPATCH_RECEIVER, extensionReceiver = null) }
    }

    private fun resolveAsExtension(level: TowerLevel): Collection<Pair<MutableResolvedCall<D>, ResolveCandidateStatus>> {
        val extensions = level.collectCandidates(name).filter { it.requiredExtensionParameter }
        return extensions.map { checkCandidate(it, ExplicitReceiverKind.EXTENSION_RECEIVER, extensionReceiver = explicitReceiver) }
    }
}

private class QualifierTowerCandidateCollector<D: CallableDescriptor>(
        context: OverloadTowerResolver.OverloadTowerResolverContext,
        val qualifier: QualifierReceiver,
        val collectCandidates: TowerLevel.(Name) -> Collection<CandidateDescriptor<D>>
): AbstractTowerCandidatesCollector<D>(context) {
    private var currentCandidates: Collection<Pair<MutableResolvedCall<D>, ResolveCandidateStatus>> = resolve()

    override fun pushTowerLevel(level: TowerLevel) {
        // no candidates, because we done all already
        currentCandidates = emptyList()
    }

    override fun pushImplicitReceiver(implicitReceiver: ReceiverParameterDescriptor) {
        // no candidates, because we done all already
        currentCandidates = emptyList()
    }

    override fun getCurrentCandidates() = currentCandidates

    private fun resolve(): Collection<Pair<MutableResolvedCall<D>, ResolveCandidateStatus>> {
        val staticMembers = QualifierTowerLevel(context.resolveTower, qualifier).collectCandidates(name)
                .filter { !it.requiredExtensionParameter }
                .map { checkCandidate(it, ExplicitReceiverKind.NO_EXPLICIT_RECEIVER, extensionReceiver = null) }
        return staticMembers
    }
}

private class NoExplicitReceiverTowerCandidateCollector<D : CallableDescriptor>(
        context: OverloadTowerResolver.OverloadTowerResolverContext,
        val collectCandidates: TowerLevel.(Name) -> Collection<CandidateDescriptor<D>>
) : AbstractTowerCandidatesCollector<D>(context) {
    private var descriptorsRequestImplicitReceiver = emptyList<CandidateDescriptor<D>>()
    private var currentDescriptors: Collection<Pair<MutableResolvedCall<D>, ResolveCandidateStatus>> = emptyList()

    override fun pushTowerLevel(level: TowerLevel) {
        val descriptors = level.collectCandidates(name)

        descriptorsRequestImplicitReceiver = descriptors.filter { it.requiredExtensionParameter }

        currentDescriptors = descriptors.filter { !it.requiredExtensionParameter }
                .map { checkCandidate(it, ExplicitReceiverKind.NO_EXPLICIT_RECEIVER, extensionReceiver = null) }
    }

    override fun pushImplicitReceiver(implicitReceiver: ReceiverParameterDescriptor) {
        currentDescriptors = descriptorsRequestImplicitReceiver
                .map { checkCandidate(it, ExplicitReceiverKind.NO_EXPLICIT_RECEIVER, extensionReceiver = implicitReceiver.value) }
    }

    override fun getCurrentCandidates() = currentDescriptors
}

private fun <D: CallableDescriptor> createSimpleCollector(
        context: OverloadTowerResolver.OverloadTowerResolverContext,
        explicitReceiver: ReceiverValue?,
        qualifier: QualifierReceiver?,
        collectCandidates: TowerLevel.(Name) -> Collection<CandidateDescriptor<D>>
) : List<TowerCandidatesCollector<D>> {
    if (explicitReceiver != null) {
        return listOf(ExplicitReceiverTowerCandidateCollector(context, explicitReceiver, collectCandidates))
    }
    else if (qualifier != null) {
        val qualifierCollector = QualifierTowerCandidateCollector(context, qualifier, collectCandidates)
        val companionObject = qualifier.getClassObjectReceiver().check { it.exists() } ?: return listOf(qualifierCollector)
        return listOf(qualifierCollector, ExplicitReceiverTowerCandidateCollector(context, companionObject, collectCandidates))
    }
    else {
        return listOf(NoExplicitReceiverTowerCandidateCollector(context, collectCandidates))
    }
}

internal fun createVariableCollector(
        context: OverloadTowerResolver.OverloadTowerResolverContext,
        explicitReceiver: ReceiverValue? = context.resolveTower.explicitReceiver,
        qualifier: QualifierReceiver? = context.resolveTower.qualifier
) = createSimpleCollector(context, explicitReceiver, qualifier) { getVariables(it) }

internal fun createFunctionCollector(
        context: OverloadTowerResolver.OverloadTowerResolverContext,
        explicitReceiver: ReceiverValue? = context.resolveTower.explicitReceiver,
        qualifier: QualifierReceiver? = context.resolveTower.qualifier
) = createSimpleCollector(context, explicitReceiver, qualifier) { getFunctions(it) }

internal fun createVariableCollectorForInvoke(
        context: OverloadTowerResolver.OverloadTowerResolverContext,
        explicitReceiver: ReceiverValue? = context.resolveTower.explicitReceiver,
        qualifier: QualifierReceiver? = context.resolveTower.qualifier
): List<TowerCandidatesCollector<VariableDescriptor>> {
    val basicCallResolutionContext = context.basicCallContext.replaceCall(CallTransformer.stripCallArguments(context.basicCallContext.call))
    val newContext = OverloadTowerResolver.OverloadTowerResolverContext(context.overloadTowerResolver, basicCallResolutionContext, context.name, context.tracing)
    return createVariableCollector(newContext, explicitReceiver, qualifier)
}