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

import org.jetbrains.kotlin.resolve.calls.results.ResolutionStatus
import org.jetbrains.kotlin.resolve.calls.results.ResolutionStatus.INCOMPLETE_TYPE_INFERENCE
import org.jetbrains.kotlin.resolve.calls.results.ResolutionStatus.SUCCESS
import org.jetbrains.kotlin.resolve.calls.results.ResolutionStatus.UNSAFE_CALL_ERROR

class ResolveCandidateStatus(val resolveCandidateLevel: ResolveCandidateLevel, val errors: List<ResolveCandidateError>)

abstract class ResolveCandidateError(val candidateLevel: ResolveCandidateLevel)

enum class ResolveCandidateLevel {
    RESOLVED, // call success or has uncompleted inference or in other words possible successful candidate
    RESOLVED_SYNTHETIC,
    MAY_THROW_RUNTIME_ERROR, // unsafe call or unstable smart cast
    RUNTIME_ERROR, // problems with visibility
    IMPOSSIBLE_TO_GENERATE, // access to outer class from nested
    OTHER // arguments not matched
}

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

class PreviousResolveError(candidateLevel: ResolveCandidateLevel): ResolveCandidateError(candidateLevel)

fun createPreviousResolveError(status: ResolutionStatus): PreviousResolveError? {
    val level = when (status) {
        SUCCESS, INCOMPLETE_TYPE_INFERENCE -> return null
        UNSAFE_CALL_ERROR -> ResolveCandidateLevel.MAY_THROW_RUNTIME_ERROR
        else -> ResolveCandidateLevel.OTHER
    }
    return PreviousResolveError(level)
}