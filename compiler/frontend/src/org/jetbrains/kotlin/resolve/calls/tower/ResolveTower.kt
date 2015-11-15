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
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValue
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.receivers.Receiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.types.KotlinType

interface ResolveTower {
    /**
     * Adds receivers to the list in order of locality, so that the closest (the most local) receiver goes first
     * All receivers with error types will be excluded
     */
    val implicitReceivers: List<ReceiverValue>

    val explicitReceiver: Receiver?

    val lexicalScope: LexicalScope

    val location: LookupLocation

    val smartCastCache: SmartCastCache

    val levels: Sequence<TowerLevel>
}

interface SmartCastCache {
    fun getDataFlowValue(receiver: ReceiverValue): DataFlowValue

    fun isStableReceiver(receiver: ReceiverValue): Boolean

    // exclude receiver.type
    fun getSmartCastPossibleTypes(receiver: ReceiverValue): Set<KotlinType>
}

interface TowerLevel {
    fun getVariables(name: Name): Collection<TowerCandidate<VariableDescriptor>>

    fun getFunctions(name: Name): Collection<TowerCandidate<FunctionDescriptor>>
}

// descriptor with matched dispatch receiver
interface TowerCandidate<out D : CallableDescriptor> {
    val errors: List<ResolveCandidateError>

    val isSynthetic: Boolean

    val requiredExtensionParameter: Boolean

    val dispatchReceiver: ReceiverValue?

    val dispatchReceiverSmartCastType: KotlinType? // todo fake override

    val descriptor: D
}

class ResolveCandidateStatus(val resolveCandidateLevel: ResolveCandidateLevel, val errors: List<ResolveCandidateError>)

enum class ResolveCandidateLevel {
    RESOLVED, // call success or has uncompleted inference or in other words possible successful candidate
    RESOLVED_SYNTHETIC,
    MAY_THROW_RUNTIME_ERROR, // unsafe call or unstable smart cast
    RUNTIME_ERROR, // problems with visibility
    IMPOSSIBLE_TO_GENERATE, // access to outer class from nested
    OTHER // arguments not matched
}

abstract class ResolveCandidateError(val candidateLevel: ResolveCandidateLevel)

// todo error for this access from nested class
class VisibilityError(val invisibleMember: DeclarationDescriptorWithVisibility): ResolveCandidateError(ResolveCandidateLevel.RUNTIME_ERROR)
class UnstableSmartCast(): ResolveCandidateError(ResolveCandidateLevel.MAY_THROW_RUNTIME_ERROR)
class ErrorDescriptor(): ResolveCandidateError(ResolveCandidateLevel.OTHER)
class NestedClassViaInstanceReference(val classDescriptor: ClassDescriptor): ResolveCandidateError(ResolveCandidateLevel.IMPOSSIBLE_TO_GENERATE)
class InnerClassViaStaticReference(val classDescriptor: ClassDescriptor): ResolveCandidateError(ResolveCandidateLevel.IMPOSSIBLE_TO_GENERATE)
class UnsupportedInnerClassCall(val message: String): ResolveCandidateError(ResolveCandidateLevel.IMPOSSIBLE_TO_GENERATE)

