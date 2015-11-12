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

package org.jetbrains.kotlin.resolve.calls.common

import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.KotlinType

/* sealed */ interface NewCall {
    val explicitReceiver: ResolvedExpression?
    val name: Name
}

interface VariableCall : NewCall {

}

interface FunctionCall : NewCall {
    // null means that there is no type parameters
    val explicitTypeParameters: List<KotlinType>?

    // For case: 'foo + bar' result is listOf('foo', 'bar')
    val argumentsInParentheses: List<NewValueArgument>

    val trailingLambda: LambdaArgument?

    val callType: CallType
}

interface ResolvedExpression {
    val incompleteResolvedCall: IncompleteResolvedCall<*>
}

/*sealed */ interface NewValueArgument {
    val name: Name

}

interface SimpleValueArgument : NewValueArgument, ResolvedExpression {
    val hasSpread: Boolean


}

interface LambdaArgument : NewValueArgument {
    val functionPlaceholder: FunctionPlaceholder
}

interface CallableReferenceArgument : NewValueArgument {

}
