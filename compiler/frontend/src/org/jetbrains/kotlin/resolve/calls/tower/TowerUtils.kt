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
import org.jetbrains.kotlin.resolve.calls.results.ResolutionStatus

fun createResolveCandidateStatus(isSynthetic: Boolean, errors: List<ResolveCandidateError>): ResolveCandidateStatus {
    if (errors.isEmpty()) {
        val level = if (isSynthetic) ResolveCandidateLevel.RESOLVED_SYNTHETIC else ResolveCandidateLevel.RESOLVED
        return ResolveCandidateStatus(level, listOf())
    }
    else {
        val level = errors.maxBy { it.candidateLevel }!!.candidateLevel
        return ResolveCandidateStatus(level, errors)
    }
}


internal fun <D : CallableDescriptor> TowerCandidate<D>.addError(error: ResolveCandidateError?): TowerCandidate<D> {
    if (error == null) return this
    return TowerCandidateImpl(dispatchReceiver, descriptor, errors + error, dispatchReceiverSmartCastType)
}

@Deprecated("Temporary error")
class PreviousResolveError(candidateLevel: ResolveCandidateLevel): ResolveCandidateError(candidateLevel)

@Deprecated("Temporary error")
fun createPreviousResolveError(status: ResolutionStatus): PreviousResolveError? {
    val level = when (status) {
        ResolutionStatus.SUCCESS, ResolutionStatus.INCOMPLETE_TYPE_INFERENCE -> return null
        ResolutionStatus.UNSAFE_CALL_ERROR -> ResolveCandidateLevel.MAY_THROW_RUNTIME_ERROR
        else -> ResolveCandidateLevel.OTHER
    }
    return PreviousResolveError(level)
}


internal infix fun <T: Any> Collection<T>.fastPlus(t: T?): Collection<T> {
    if (t == null) return this

    if (isEmpty()) return listOf(t)

    return this + t
}

internal infix fun <T: Any> Collection<T>.fastPlus(other: Collection<T>?): Collection<T> {
    if (other == null || other.isEmpty()) return this

    if (isEmpty()) return other

    return this + other
}

