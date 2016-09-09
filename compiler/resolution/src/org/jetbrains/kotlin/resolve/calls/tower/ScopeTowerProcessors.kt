/*
 * Copyright 2010-2016 JetBrains s.r.o.
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
import org.jetbrains.kotlin.resolve.coroutine.CoroutineReceiverValue
import org.jetbrains.kotlin.resolve.coroutine.createCoroutineSuspensionFunctionView
import org.jetbrains.kotlin.resolve.scopes.receivers.DetailedReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.QualifierReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValueWithSmartCastInfo
import org.jetbrains.kotlin.utils.addToStdlib.check


class KnownResultProcessor<out C>(
        val result: Collection<C>
): ScopeTowerProcessor<C> {
    override fun process(data: TowerData)
            = if (data == TowerData.Empty) listOfNotNull(result.check { it.isNotEmpty() }) else emptyList()
}

class CompositeScopeTowerProcessor<out C>(
        vararg val processors: ScopeTowerProcessor<C>
) : ScopeTowerProcessor<C> {
    override fun process(data: TowerData): List<Collection<C>> = processors.flatMap { it.process(data) }
}

internal abstract class AbstractSimpleScopeTowerProcessor<C: Candidate>(
        val candidateFactory: CandidateFactory<C>
) : ScopeTowerProcessor<C> {

    protected abstract fun simpleProcess(data: TowerData): Collection<C>

    override fun process(data: TowerData): List<Collection<C>> = listOfNotNull(simpleProcess(data).check { it.isNotEmpty() })
}

internal class ExplicitReceiverScopeTowerProcessor<C: Candidate>(
        val scopeTower: ImplicitScopeTower,
        context: CandidateFactory<C>,
        val explicitReceiver: ReceiverValueWithSmartCastInfo,
        val collectCandidates: ScopeTowerLevel.(extensionReceiver: ReceiverValueWithSmartCastInfo?) -> Collection<CandidateWithBoundDispatchReceiver>
): AbstractSimpleScopeTowerProcessor<C>(context) {
    override fun simpleProcess(data: TowerData): Collection<C> {
        return when (data) {
            TowerData.Empty -> resolveAsMember()
            is TowerData.TowerLevel -> resolveAsExtension(data.level)
            else -> emptyList()
        }
    }

    private fun resolveAsMember(): Collection<C> {
        val members = ReceiverScopeTowerLevel(scopeTower, explicitReceiver)
                .collectCandidates(null).filter { !it.requiresExtensionReceiver }
        return members.map { candidateFactory.createCandidate(it, ExplicitReceiverKind.DISPATCH_RECEIVER, extensionReceiver = null) }
    }

    private fun resolveAsExtension(level: ScopeTowerLevel): Collection<C> {
        val extensions = level.collectCandidates(explicitReceiver).filter { it.requiresExtensionReceiver }
        return extensions.map { candidateFactory.createCandidate(it, ExplicitReceiverKind.EXTENSION_RECEIVER, extensionReceiver = explicitReceiver) }
    }
}

private class QualifierScopeTowerProcessor<C: Candidate>(
        val scopeTower: ImplicitScopeTower,
        context: CandidateFactory<C>,
        val qualifier: QualifierReceiver,
        val collectCandidates: ScopeTowerLevel.(extensionReceiver: ReceiverValueWithSmartCastInfo?) -> Collection<CandidateWithBoundDispatchReceiver>
): AbstractSimpleScopeTowerProcessor<C>(context) {
    override fun simpleProcess(data: TowerData): Collection<C> {
        if (data != TowerData.Empty) return emptyList()

        val staticMembers = QualifierScopeTowerLevel(scopeTower, qualifier).collectCandidates(null)
                .filter { !it.requiresExtensionReceiver }
                .map { candidateFactory.createCandidate(it, ExplicitReceiverKind.NO_EXPLICIT_RECEIVER, extensionReceiver = null) }
        return staticMembers
    }
}

private class NoExplicitReceiverScopeTowerProcessor<C: Candidate>(
        context: CandidateFactory<C>,
        val collectCandidates: ScopeTowerLevel.(extensionReceiver: ReceiverValueWithSmartCastInfo?) -> Collection<CandidateWithBoundDispatchReceiver>
) : AbstractSimpleScopeTowerProcessor<C>(context) {
    override fun simpleProcess(data: TowerData): Collection<C>
            = when(data) {
                is TowerData.TowerLevel -> {
                    data.level.collectCandidates(null).filter { !it.requiresExtensionReceiver }.map {
                        candidateFactory.createCandidate(it, ExplicitReceiverKind.NO_EXPLICIT_RECEIVER, extensionReceiver = null)
                    }
                }
                is TowerData.BothTowerLevelAndImplicitReceiver -> {
                    val result = mutableListOf<C>()

                    data.level.collectCandidates(data.implicitReceiver).filter { it.requiresExtensionReceiver }.forEach {
                        result.add(
                            candidateFactory.createCandidate(
                                    it, ExplicitReceiverKind.NO_EXPLICIT_RECEIVER, extensionReceiver = data.implicitReceiver))

                        if (data.implicitReceiver.receiverValue is CoroutineReceiverValue) {
                            val newDescriptor = it.descriptor.createCoroutineSuspensionFunctionView() ?: return@forEach
                            result.add(
                                candidateFactory.createCandidate(
                                        it.copy(newDescriptor = newDescriptor),
                                        ExplicitReceiverKind.NO_EXPLICIT_RECEIVER, extensionReceiver = data.implicitReceiver))
                        }
                    }

                    result
                }
                else -> emptyList()
            }
}

private fun <C : Candidate> createSimpleProcessor(
        scopeTower: ImplicitScopeTower,
        context: CandidateFactory<C>,
        explicitReceiver: DetailedReceiver?,
        classValueReceiver: Boolean,
        collectCandidates: ScopeTowerLevel.(extensionReceiver: ReceiverValueWithSmartCastInfo?) -> Collection<CandidateWithBoundDispatchReceiver>
) : ScopeTowerProcessor<C> {
    if (explicitReceiver is ReceiverValueWithSmartCastInfo) {
        return ExplicitReceiverScopeTowerProcessor(scopeTower, context, explicitReceiver, collectCandidates)
    }
    else if (explicitReceiver is QualifierReceiver) {
        val qualifierProcessor = QualifierScopeTowerProcessor(scopeTower, context, explicitReceiver, collectCandidates)
        if (!classValueReceiver) return qualifierProcessor

        // todo enum entry, object.
        val classValue = explicitReceiver.classValueReceiverWithSmartCastInfo ?: return qualifierProcessor
        return CompositeScopeTowerProcessor(
                qualifierProcessor,
                ExplicitReceiverScopeTowerProcessor(scopeTower, context, classValue, collectCandidates)
        )
    }
    else {
        assert(explicitReceiver == null) {
            "Illegal explicit receiver: $explicitReceiver(${explicitReceiver!!.javaClass.simpleName})"
        }
        return NoExplicitReceiverScopeTowerProcessor(context, collectCandidates)
    }
}

fun <C : Candidate> createVariableProcessor(scopeTower: ImplicitScopeTower, name: Name,
                                            context: CandidateFactory<C>, explicitReceiver: DetailedReceiver?, classValueReceiver: Boolean = true
) = createSimpleProcessor(scopeTower, context, explicitReceiver, classValueReceiver) { getVariables(name, it) }

fun <C : Candidate> createVariableAndObjectProcessor(scopeTower: ImplicitScopeTower, name: Name,
                                                     context: CandidateFactory<C>, explicitReceiver: DetailedReceiver?, classValueReceiver: Boolean = true
) = CompositeScopeTowerProcessor(
        createVariableProcessor(scopeTower, name, context, explicitReceiver),
        createSimpleProcessor(scopeTower, context, explicitReceiver, classValueReceiver) { getObjects(name, it) }
)

fun <C : Candidate> createSimpleFunctionProcessor(scopeTower: ImplicitScopeTower, name: Name,
                                                  context: CandidateFactory<C>, explicitReceiver: DetailedReceiver?, classValueReceiver: Boolean = true
) = createSimpleProcessor(scopeTower, context, explicitReceiver, classValueReceiver) { getFunctions(name, it) }


fun <С: Candidate> createFunctionProcessor(
        scopeTower: ImplicitScopeTower,
        name: Name,
        simpleContext: CandidateFactory<С>,
        factoryProviderForInvoke: CandidateFactoryProviderForInvoke<С>,
        explicitReceiver: DetailedReceiver?
): CompositeScopeTowerProcessor<С> {

    // a.foo() -- simple function call
    val simpleFunction = createSimpleFunctionProcessor(scopeTower, name, simpleContext, explicitReceiver)

    // a.foo() -- property a.foo + foo.invoke()
    val invokeProcessor = InvokeTowerProcessor(scopeTower, name, factoryProviderForInvoke, explicitReceiver)

    // a.foo() -- property foo is extension function with receiver a -- a.invoke()
    val invokeExtensionProcessor = createProcessorWithReceiverValueOrEmpty(explicitReceiver) {
        InvokeExtensionTowerProcessor(scopeTower, name, factoryProviderForInvoke, it)
    }

    return CompositeScopeTowerProcessor(simpleFunction, invokeProcessor, invokeExtensionProcessor)
}


fun <C: Candidate> createProcessorWithReceiverValueOrEmpty(
        explicitReceiver: DetailedReceiver?,
        create: (ReceiverValueWithSmartCastInfo?) -> ScopeTowerProcessor<C>
): ScopeTowerProcessor<C> {
    return if (explicitReceiver is QualifierReceiver) {
        explicitReceiver.classValueReceiverWithSmartCastInfo?.let(create)
        ?: KnownResultProcessor<C>(listOf())
    }
    else {
        create(explicitReceiver as ReceiverValueWithSmartCastInfo?)
    }
}