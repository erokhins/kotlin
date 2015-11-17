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

import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.scopes.receivers.ClassQualifier
import org.jetbrains.kotlin.resolve.scopes.receivers.QualifierReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.Receiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.utils.addToStdlib.check


internal class KnownResultCandidatesCollector<Candidate>(
        result: Collection<Candidate>
): TowerCandidatesCollector<Candidate> {
    var candidates = result

    override fun pushTowerLevel(level: TowerLevel) {
        candidates = emptyList()
    }

    override fun pushImplicitReceiver(implicitReceiver: ReceiverValue) {
        candidates = emptyList()
    }

    override fun getCandidatesGroups() = listOfNotNull(candidates.check { it.isNotEmpty() })
}

internal class CompositeTowerCandidatesCollector<Candidate>(
        vararg val collectors: TowerCandidatesCollector<Candidate>
) : TowerCandidatesCollector<Candidate> {
    override fun pushTowerLevel(level: TowerLevel) = collectors.forEach { it.pushTowerLevel(level) }

    override fun pushImplicitReceiver(implicitReceiver: ReceiverValue)
            = collectors.forEach { it.pushImplicitReceiver(implicitReceiver) }

    override fun getCandidatesGroups(): List<Collection<Candidate>> = collectors.flatMap { it.getCandidatesGroups() }
}

internal abstract class AbstractTowerCandidatesCollector<Candidate>(
        val context: TowerContext<Candidate>
) : TowerCandidatesCollector<Candidate> {
    protected val name: Name get() = context.name

    protected abstract var candidates: Collection<Candidate>

    override fun getCandidatesGroups() = listOfNotNull(candidates.check { it.isNotEmpty() })
}

internal class ExplicitReceiverTowerCandidateCollector<Candidate>(
        context: TowerContext<Candidate>,
        val explicitReceiver: ReceiverValue,
        val collectCandidates: TowerLevel.(Name) -> Collection<TowerCandidate<*>>
): AbstractTowerCandidatesCollector<Candidate>(context) {
    override var candidates = resolveAsMember()

    override fun pushTowerLevel(level: TowerLevel) {
        candidates = resolveAsExtension(level)
    }

    override fun pushImplicitReceiver(implicitReceiver: ReceiverValue) {
        // no candidates, because we already have receiver
        candidates = emptyList()
    }

    private fun resolveAsMember(): Collection<Candidate> {
        val members = ReceiverTowerLevel(context.resolveTower, explicitReceiver).collectCandidates(name).filter { !it.requiredExtensionParameter }
        return members.map { context.createCandidate(it, ExplicitReceiverKind.DISPATCH_RECEIVER, extensionReceiver = null) }
    }

    private fun resolveAsExtension(level: TowerLevel): Collection<Candidate> {
        val extensions = level.collectCandidates(name).filter { it.requiredExtensionParameter }
        return extensions.map { context.createCandidate(it, ExplicitReceiverKind.EXTENSION_RECEIVER, extensionReceiver = explicitReceiver) }
    }
}

private class QualifierTowerCandidateCollector<Candidate>(
        context: TowerContext<Candidate>,
        val qualifier: QualifierReceiver,
        val collectCandidates: TowerLevel.(Name) -> Collection<TowerCandidate<*>>
): AbstractTowerCandidatesCollector<Candidate>(context) {
    override var candidates = resolve()

    override fun pushTowerLevel(level: TowerLevel) {
        // no candidates, because we done all already
        candidates = emptyList()
    }

    override fun pushImplicitReceiver(implicitReceiver: ReceiverValue) {
        // no candidates, because we done all already
        candidates = emptyList()
    }

    private fun resolve(): Collection<Candidate> {
        val staticMembers = QualifierTowerLevel(context.resolveTower, qualifier).collectCandidates(name)
                .filter { !it.requiredExtensionParameter }
                .map { context.createCandidate(it, ExplicitReceiverKind.NO_EXPLICIT_RECEIVER, extensionReceiver = null) }
        return staticMembers
    }
}

private class NoExplicitReceiverTowerCandidateCollector<Candidate>(
        context: TowerContext<Candidate>,
        val collectCandidates: TowerLevel.(Name) -> Collection<TowerCandidate<*>>
) : AbstractTowerCandidatesCollector<Candidate>(context) {
    override var candidates: Collection<Candidate> = emptyList()

    private var descriptorsRequestImplicitReceiver = emptyList<TowerCandidate<*>>()

    override fun pushTowerLevel(level: TowerLevel) {
        val descriptors = level.collectCandidates(name)

        descriptorsRequestImplicitReceiver = descriptors.filter { it.requiredExtensionParameter }

        candidates = descriptors.filter { !it.requiredExtensionParameter }
                .map { context.createCandidate(it, ExplicitReceiverKind.NO_EXPLICIT_RECEIVER, extensionReceiver = null) }
    }

    override fun pushImplicitReceiver(implicitReceiver: ReceiverValue) {
        candidates = descriptorsRequestImplicitReceiver
                .map { context.createCandidate(it, ExplicitReceiverKind.NO_EXPLICIT_RECEIVER, extensionReceiver = implicitReceiver) }
    }

}

private fun <Candidate> createSimpleCollector(
        context: TowerContext<Candidate>,
        explicitReceiver: Receiver?,
        collectCandidates: TowerLevel.(Name) -> Collection<TowerCandidate<*>>
) : TowerCandidatesCollector<Candidate> {
    if (explicitReceiver is ReceiverValue) {
        return ExplicitReceiverTowerCandidateCollector(context, explicitReceiver, collectCandidates)
    }
    else if (explicitReceiver is QualifierReceiver) {
        val qualifierCollector = QualifierTowerCandidateCollector(context, explicitReceiver, collectCandidates)

        // todo enum entry, object.
        val companionObject = (explicitReceiver as? ClassQualifier)?.companionObjectReceiver ?: return qualifierCollector
        return CompositeTowerCandidatesCollector(
                qualifierCollector,
                ExplicitReceiverTowerCandidateCollector(context, companionObject, collectCandidates)
        )
    }
    else {
        assert(explicitReceiver == null) {
            "Illegal explicit receiver: $explicitReceiver(${explicitReceiver!!.javaClass.simpleName})"
        }
        return NoExplicitReceiverTowerCandidateCollector(context, collectCandidates)
    }
}

internal fun <Candidate> createVariableCollector(context: TowerContext<Candidate>, explicitReceiver: Receiver?)
        = createSimpleCollector(context, explicitReceiver, TowerLevel::getVariables)

internal fun <Candidate> createFunctionCollector(context: TowerContext<Candidate>, explicitReceiver: Receiver?)
        = createSimpleCollector(context, explicitReceiver, TowerLevel::getFunctions)
