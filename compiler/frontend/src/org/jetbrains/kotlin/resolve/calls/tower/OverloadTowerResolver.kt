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
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.TemporaryBindingTrace
import org.jetbrains.kotlin.resolve.calls.CandidateResolver
import org.jetbrains.kotlin.resolve.calls.callUtil.createLookupLocation
import org.jetbrains.kotlin.resolve.calls.context.BasicCallResolutionContext
import org.jetbrains.kotlin.resolve.calls.context.CallCandidateResolutionContext
import org.jetbrains.kotlin.resolve.calls.context.CandidateResolveMode
import org.jetbrains.kotlin.resolve.calls.context.CheckArgumentTypesMode
import org.jetbrains.kotlin.resolve.calls.model.MutableResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCallImpl
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCallImpl
import org.jetbrains.kotlin.resolve.calls.results.OverloadResolutionResultsImpl
import org.jetbrains.kotlin.resolve.calls.results.ResolutionResultsHandler
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.calls.tasks.TracingStrategy
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.utils.addToStdlib.check

public class OverloadTowerResolver(
        val candidateResolver: CandidateResolver,
        val resolutionResultsHandler: ResolutionResultsHandler
) {

    public data class OverloadTowerResolverContext(
            val overloadTowerResolver: OverloadTowerResolver,
            val basicCallContext: BasicCallResolutionContext,
            val name: Name,
            val tracing: TracingStrategy
    ) {

        val resolveTower: ResolveTower = ResolveTowerImpl(basicCallContext,
                                                          basicCallContext.call.explicitReceiver.check { it.exists() }
                                                          ?: basicCallContext.call.dispatchReceiver.check { it.exists() }, // yes, it is hack for a()()
                                                          basicCallContext.call.createLookupLocation())
    }

    public fun runVariableResolve(basicCallContext: BasicCallResolutionContext, name: Name, tracing: TracingStrategy): OverloadResolutionResultsImpl<VariableDescriptor> {
        val towerContext = OverloadTowerResolverContext(this, basicCallContext, name, tracing)
        return runResolve(towerContext, createVariableCollector(towerContext))
    }

    public fun runFunctionResolve(basicCallContext: BasicCallResolutionContext, name: Name, tracing: TracingStrategy): OverloadResolutionResultsImpl<FunctionDescriptor> {
        val towerContext = OverloadTowerResolverContext(this, basicCallContext, name, tracing)

        createCallTowerCollectorsForExplicitInvoke(towerContext)?.let {
            return runResolve(towerContext, listOf(it))
        }

        return runResolve(towerContext, createFunctionCollector(towerContext) + InvokeCollector(towerContext) + InvokeExtensionCollector(towerContext))
    }

    public fun runCallableResolve(basicCallContext: BasicCallResolutionContext, name: Name, tracing: TracingStrategy): OverloadResolutionResultsImpl<CallableDescriptor> {
        val towerContext = OverloadTowerResolverContext(this, basicCallContext, name, tracing)
        val list = createVariableCollector(towerContext) + createFunctionCollector(towerContext)
        return runResolve(towerContext, list as List<TowerCandidatesCollector<CallableDescriptor>>, useOrder = false) // this cast is correct, but in java impossible define declared side variance (on ResolvedCall)
    }

    private fun <D: CallableDescriptor> runResolve(
            towerContext: OverloadTowerResolverContext,
            candidatesCollectors: List<TowerCandidatesCollector<D>>,
            useOrder: Boolean = true
    ): OverloadResolutionResultsImpl<D> {

        val resultCollector = ResultCollector<D>()

        fun pushSomething(action: TowerCandidatesCollector<D>.() -> Unit): OverloadResolutionResultsImpl<D>? {
            if (useOrder) {
                candidatesCollectors.forEach {
                    it.action()
                    resultCollector.pushCandidates(it.getCurrentCandidates())
                    resultCollector.getResolved()?.let { return convertToOverloadResults(towerContext, it) }
                }
            }
            else {
                candidatesCollectors.forEach { it.action() }
                resultCollector.pushCandidates(candidatesCollectors.flatMap { it.getCurrentCandidates() })
                resultCollector.getResolved()?.let { return convertToOverloadResults(towerContext, it) }
            }
            return null
        }

        // possible there is explicit member
        pushSomething {  }?.let { return it }

        for (level in towerContext.resolveTower.levels) {
            pushSomething { pushTowerLevel(level) }?.let { return it }

            for (implicitReceiver in towerContext.resolveTower.implicitReceiversHierarchy) {
                pushSomething { pushImplicitReceiver(implicitReceiver) }?.let { return it }
            }
        }

        return convertToOverloadResults(towerContext, resultCollector.getFinalCandidates())
    }

    internal fun <D : CallableDescriptor> convertToOverloadResults(
            context: OverloadTowerResolverContext,
            candidates: Collection<Pair<MutableResolvedCall<D>, ResolveCandidateStatus>>
    ): OverloadResolutionResultsImpl<D> {
        val resolvedCalls = candidates.map {
            val (resolvedCall, status) = it
            if (resolvedCall is VariableAsFunctionResolvedCallImpl) {
                // todo hacks
                context.tracing.bindReference(resolvedCall.variableCall.trace, resolvedCall.variableCall)
                context.tracing.bindResolvedCall(resolvedCall.variableCall.trace, resolvedCall)

                resolvedCall.variableCall.trace.addOwnDataTo(resolvedCall.functionCall.trace)

                resolvedCall.functionCall.tracingStrategy.bindReference(resolvedCall.functionCall.trace, resolvedCall.functionCall)
//                resolvedCall.hackInvokeTracing.bindResolvedCall(resolvedCall.functionCall.trace, resolvedCall)
            } else {
                context.tracing.bindReference(resolvedCall.trace, resolvedCall)
                context.tracing.bindResolvedCall(resolvedCall.trace, resolvedCall)
            }

            if (resolvedCall.status.possibleTransformToSuccess()) {
                for (error in status.errors) {
                    if (error is UnsupportedInnerClassCall) {
                        resolvedCall.trace.report(Errors.UNSUPPORTED.on(resolvedCall.call.callElement, error.message))
                    }
                    else if (error is NestedClassViaInstanceReference) {
                        context.tracing.nestedClassAccessViaInstanceReference(resolvedCall.trace, error.classDescriptor, resolvedCall.explicitReceiverKind)
                    }
                    else if (error is ErrorDescriptor) {
                        return@map null
                    }
                }
            }

            resolvedCall
        }.filterNotNull()

        val result = resolutionResultsHandler.computeResultAndReportErrors(context.basicCallContext, context.tracing, resolvedCalls)
        return result
    }

    internal fun <D : CallableDescriptor> checkCandidate(
            towerContext: OverloadTowerResolverContext,
            candidate: CandidateDescriptor<D>,
            explicitReceiverKind: ExplicitReceiverKind,
            extensionReceiver: ReceiverValue?
    ): Pair<MutableResolvedCall<D>, ResolveCandidateStatus> {
        val candidateTrace = TemporaryBindingTrace.create(towerContext.basicCallContext.trace, "Context for resolve candidate")
        val candidateCall = ResolvedCallImpl(
                towerContext.basicCallContext.call, candidate.descriptor,
                candidate.dispatchReceiver ?: ReceiverValue.NO_RECEIVER, extensionReceiver ?: ReceiverValue.NO_RECEIVER,
                explicitReceiverKind, null, candidateTrace, towerContext.tracing,
                towerContext.basicCallContext.dataFlowInfoForArguments // todo may be we should create new mutable info for arguments
        )
        val callCandidateResolutionContext = CallCandidateResolutionContext.create(
                candidateCall, towerContext.basicCallContext, candidateTrace, towerContext.tracing, towerContext.basicCallContext.call,
                ReceiverValue.NO_RECEIVER, CandidateResolveMode.FULLY // todo
        )
        candidateResolver.performResolutionForCandidateCall(callCandidateResolutionContext, towerContext.basicCallContext.checkArguments) // todo

        val errors = (candidate.errors + createPreviousResolveError(candidateCall.status)).filterNotNull() // todo optimize
        return candidateCall to createResolveCandidateStatus(candidate.isSynthetic, errors)
    }
}

// collect all candidates
internal open class ResultCollector<D : CallableDescriptor> {
    private var currentCandidates: Collection<Pair<MutableResolvedCall<D>, ResolveCandidateStatus>> = emptyList()
    private var currentLevel: ResolveCandidateLevel? = null

    fun getResolved() = currentCandidates.check { currentLevel == ResolveCandidateLevel.RESOLVED }

    fun getSyntheticResolved() = currentCandidates.check { currentLevel == ResolveCandidateLevel.RESOLVED_SYNTHETIC }

    fun getErrors() = currentCandidates.check {
        currentLevel == null || currentLevel!! > ResolveCandidateLevel.RESOLVED_SYNTHETIC
    }

    fun getFinalCandidates() = getResolved() ?: getSyntheticResolved() ?: getErrors() ?: emptyList()

    fun pushCandidates(candidates: Collection<Pair<MutableResolvedCall<D>, ResolveCandidateStatus>>) {
        if (candidates.isEmpty()) return
        val minimalLevel = candidates.minBy { it.second.resolveCandidateLevel }!!.second.resolveCandidateLevel
        if (currentLevel == null || currentLevel!! > minimalLevel) {
            currentLevel = minimalLevel
            currentCandidates = candidates.filter { it.second.resolveCandidateLevel == minimalLevel }
        }
    }
}