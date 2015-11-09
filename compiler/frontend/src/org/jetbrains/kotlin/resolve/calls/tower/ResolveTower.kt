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

import org.jetbrains.kotlin.descriptors.ReceiverParameterDescriptor
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.resolve.calls.context.ResolutionContext
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValue
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValueFactory
import org.jetbrains.kotlin.resolve.scopes.ImportingScope
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.receivers.Qualifier
import org.jetbrains.kotlin.resolve.scopes.receivers.QualifierReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.resolve.scopes.utils.getImplicitReceiversHierarchy
import org.jetbrains.kotlin.resolve.scopes.utils.parentsWithSelf
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.addToStdlib.check
import org.jetbrains.kotlin.utils.addToStdlib.singletonList
import java.util.*

public interface ResolveTower {
    /**
     * Adds receivers to the list in order of locality, so that the closest (the most local) receiver goes first
     */
    val implicitReceiversHierarchy: List<ReceiverParameterDescriptor>

    val explicitReceiver: ReceiverValue?

    val qualifier: QualifierReceiver?

    val location: LookupLocation

    val resolutionContext: ResolutionContext<*>

    val smartCastCache: SmartCastCache

    val levels: Sequence<TowerLevel>
}

public class SmartCastCache(private val resolutionContext: ResolutionContext<*>) {
    private val dataFlowInfo = resolutionContext.dataFlowInfo
    private val smartCastInfoCache = HashMap<ReceiverValue, SmartCastInfo>()

    private fun getSmartCastInfo(receiver: ReceiverValue): SmartCastInfo
            = smartCastInfoCache.getOrPut(receiver) {
        val dataFlowValue = DataFlowValueFactory.createDataFlowValue(receiver, resolutionContext)
        SmartCastInfo(dataFlowValue, dataFlowInfo.getPossibleTypes(dataFlowValue))
    }

    public fun getDataFlowValue(receiver: ReceiverValue): DataFlowValue = getSmartCastInfo(receiver).dataFlowValue

    public fun isStableReceiver(receiver: ReceiverValue): Boolean = getSmartCastInfo(receiver).dataFlowValue.isPredictable

    // exclude receiver.type
    public fun getSmartCastPossibleTypes(receiver: ReceiverValue): Set<KotlinType> = getSmartCastInfo(receiver).possibleTypes

    private data class SmartCastInfo(val dataFlowValue: DataFlowValue, val possibleTypes: Set<KotlinType>)
}


internal class ResolveTowerImpl(
        override val resolutionContext: ResolutionContext<*>,
        receiver: ReceiverValue?,
        override val location: LookupLocation
): ResolveTower {
    override val explicitReceiver: ReceiverValue? = receiver?.check { it !is Qualifier }
    override val qualifier: QualifierReceiver? = receiver as? QualifierReceiver

    override val smartCastCache = SmartCastCache(resolutionContext)

    override val implicitReceiversHierarchy = resolutionContext.scope.getImplicitReceiversHierarchy()

    // todo val?
    override val levels: Sequence<TowerLevel> = createPrototypeLevels().asSequence().map { it.asTowerLevel(this) }

    // we shouldn't calculate this before we entrance to some importing scope
    private val allKnownReceivers by lazy(LazyThreadSafetyMode.PUBLICATION) {
        (explicitReceiver?.singletonList() ?: implicitReceiversHierarchy.map { it.value }).flatMap {
            smartCastCache.getSmartCastPossibleTypes(it) fastPlus it.type
        }
    }

    private sealed class LevelPrototype {
        abstract fun asTowerLevel(resolveTower: ResolveTower): TowerLevel

        class LocalScope(val lexicalScope: LexicalScope): LevelPrototype() {
            override fun asTowerLevel(resolveTower: ResolveTower) = ScopeTowerLevel(resolveTower, lexicalScope)
        }

        class Scope(val lexicalScope: LexicalScope): LevelPrototype() {
            override fun asTowerLevel(resolveTower: ResolveTower) = ScopeTowerLevel(resolveTower, lexicalScope)
        }

        class Receiver(val implicitReceiver: ReceiverParameterDescriptor): LevelPrototype() {
            override fun asTowerLevel(resolveTower: ResolveTower) = ReceiverTowerLevel(resolveTower, implicitReceiver.value)
        }

        class ImportingScopeLevel(val importingScope: ImportingScope, val lazyAllKnowReceivers: () -> List<KotlinType>): LevelPrototype() {
            override fun asTowerLevel(resolveTower: ResolveTower) = ImportingScopeTowerLevel(resolveTower, importingScope, lazyAllKnowReceivers())
        }
    }

    private fun createPrototypeLevels(): List<LevelPrototype> {
        val result = ArrayList<LevelPrototype>()

        // locals win
        result.addAll(resolutionContext.scope.parentsWithSelf.
                filter { it is LexicalScope && it.kind.local }.
                map { LevelPrototype.LocalScope(it as LexicalScope) })

        resolutionContext.scope.parentsWithSelf.forEach { scope ->
            if (scope is LexicalScope) {
                if (!scope.kind.local) result.add(LevelPrototype.Scope(scope))

                scope.implicitReceiver?.let { result.add(LevelPrototype.Receiver(it)) }
            }
            else {
                result.add(LevelPrototype.ImportingScopeLevel(scope as ImportingScope, { allKnownReceivers }))
            }
        }

        return result
    }

}