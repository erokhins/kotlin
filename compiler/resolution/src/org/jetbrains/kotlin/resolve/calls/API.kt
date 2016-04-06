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

package org.jetbrains.kotlin.resolve.calls

import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.KotlinType

interface CallArgument {
    val isSpread: Boolean
    val argumentName: Name?
}

interface ExpressionArgument : CallArgument {
    val type: KotlinType
}

interface SubCall : CallArgument {
    val resolvedCall: NewResolvedCall
}

interface LambdaArgument : CallArgument {
    override val isSpread: Boolean
        get() = false // todo error
}

interface CallableReferenceArgument : CallArgument {
    override val isSpread: Boolean
        get() = false // todo error on call -- function type is not subtype of Array<out ...>
}

interface NewCall {
    val explicitReceiver: CallArgument?

    val argumentsInParenthesis: List<CallArgument>

    val externalLambda: LambdaArgument?

    val typeArguments: List<KotlinType>

    val isSafeCall: Boolean

    val diagnosticReporter: DiagnosticReporter
}

interface CallDiagnostic {
    fun report(reporter: DiagnosticReporter)
}

interface DiagnosticReporter {
    fun reportOnCallOperationNode(diagnostic: CallDiagnostic)
}


//-----------------------------

interface NewResolvedCall


