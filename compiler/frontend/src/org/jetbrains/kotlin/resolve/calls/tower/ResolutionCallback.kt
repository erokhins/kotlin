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

import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.resolve.calls.context.ContextDependency
import org.jetbrains.kotlin.resolve.calls.context.ResolutionContext
import org.jetbrains.kotlin.types.expressions.KotlinTypeInfo

interface ResolutionCallback {
    object Empty: ResolutionCallback {
        override fun getContextDependencyForReturnExpression(returnExpression: KtReturnExpression) = ContextDependency.INDEPENDENT
        override fun returnStatement(context: ResolutionContext<*>, returnExpression: KtReturnExpression, typeInfoForReturnExpression: KotlinTypeInfo) {}
        override fun lastStatement(context: ResolutionContext<*>, expression: KtExpression, typeInfo: KotlinTypeInfo) {}
    }

    fun returnStatement(
            context: ResolutionContext<*>,
            returnExpression: KtReturnExpression,
            typeInfoForReturnExpression: KotlinTypeInfo
    )

    fun getContextDependencyForReturnExpression(returnExpression: KtReturnExpression): ContextDependency

    fun lastStatement(
            context: ResolutionContext<*>,
            expression: KtExpression,
            typeInfo: KotlinTypeInfo
    )
}