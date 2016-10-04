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

import org.jetbrains.kotlin.builtins.getValueParameterTypesFromFunctionType
import org.jetbrains.kotlin.builtins.isExtensionFunctionType
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.resolve.calls.inference.ConstraintSystemBuilder
import org.jetbrains.kotlin.resolve.calls.inference.model.ArgumentConstraintPosition
import org.jetbrains.kotlin.resolve.calls.inference.model.LambdaTypeVariable
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.tower.isSuccess
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.types.typeUtil.builtIns
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.kotlin.utils.addIfNotNull
import java.lang.UnsupportedOperationException

internal object CheckArguments : ResolutionPart {
    override fun SimpleResolutionCandidate.process(): List<CallDiagnostic> {
        val diagnostics = SmartList<CallDiagnostic>()
        for (parameterDescriptor in descriptorWithFreshTypes.valueParameters) {
            // error was reported in ArgumentsToParametersMapper
            val resolvedCallArgument = argumentMappingByOriginal[parameterDescriptor.original] ?: continue
            for (argument in resolvedCallArgument.arguments) {

                val diagnostic = checkArgument(callContext, csBuilder, argument, argument.getExpectedType(parameterDescriptor))
                diagnostics.addIfNotNull(diagnostic)

                if (diagnostic != null && !diagnostic.candidateApplicability.isSuccess) break
            }
        }
        return diagnostics
    }

    fun checkArgument(
            callContext: CallContext,
            csBuilder: ConstraintSystemBuilder,
            argument: CallArgument,
            expectedType: UnwrappedType
    ): CallDiagnostic? {
        return when (argument) {
            is ExpressionArgument -> checkExpressionArgument(csBuilder, argument, expectedType, isReceiver = false)
            is SubCallArgument -> checkSubCallArgument(csBuilder, argument, expectedType, isReceiver = false)
            is LambdaArgument -> processLambdaArgument(callContext.astCall, csBuilder, argument, expectedType)
            is CallableReferenceArgument -> processCallableReferenceArgument(callContext, csBuilder, argument, expectedType)
            else -> error("Incorrect argument type: $argument, ${argument.javaClass.canonicalName}.")
        }
    }

    inline fun computeParameterTypes(
            argument: LambdaArgument,
            expectedType: UnwrappedType,
            createFreshType: () -> UnwrappedType
    ): List<UnwrappedType> {
        argument.parametersTypes?.map { it ?: createFreshType() } ?.let { return it }

        if (expectedType.isFunctionType) {
            return getValueParameterTypesFromFunctionType(expectedType).map { createFreshType() }
        }

        // if expected type is non-functional type and there is no declared parameters
        return emptyList()
    }

    inline fun computeReceiver(
            argument: LambdaArgument,
            expectedType: UnwrappedType,
            createFreshType: () -> UnwrappedType
    ) : UnwrappedType? {
        if (argument is FunctionExpression) return argument.receiverType

        if (expectedType.isExtensionFunctionType) return createFreshType()

        return null
    }

    inline fun computeReturnType(
            argument: LambdaArgument,
            createFreshType: () -> UnwrappedType
    ) : UnwrappedType {
        if (argument is FunctionExpression) return argument.receiverType ?: createFreshType()

        return createFreshType()
    }

    fun processLambdaArgument(
            astCall: ASTCall,
            csBuilder: ConstraintSystemBuilder,
            argument: LambdaArgument,
            expectedType: UnwrappedType
    ): CallDiagnostic? {
        // initial checks
        if (expectedType.isFunctionType) {
            val expectedParameterCount = getValueParameterTypesFromFunctionType(expectedType).size

            argument.parametersTypes?.size?.let {
                if (expectedParameterCount != it) return ExpectedLambdaParametersCountMismatch(argument, expectedParameterCount, it)
            }

            if (argument is FunctionExpression) {
                if (argument.receiverType != null && !expectedType.isExtensionFunctionType) return UnexpectedReceiver(argument)
                if (argument.receiverType == null && expectedType.isExtensionFunctionType) return MissingReceiver(argument)
            }
        }

        val builtIns = expectedType.builtIns
        val freshVariables = SmartList<LambdaTypeVariable>()
        val receiver = computeReceiver(argument, expectedType) {
            LambdaTypeVariable(argument, LambdaTypeVariable.Kind.RECEIVER, builtIns).apply { freshVariables.add(this) }.defaultType
        }

        val parameters = computeParameterTypes(argument, expectedType) {
            LambdaTypeVariable(argument, LambdaTypeVariable.Kind.PARAMETER, builtIns).apply { freshVariables.add(this) }.defaultType
        }

        val returnType = computeReturnType(argument) {
            LambdaTypeVariable(argument, LambdaTypeVariable.Kind.RETURN_TYPE, builtIns).apply { freshVariables.add(this) }.defaultType
        }

        val resolvedArgument = ResolvedLambdaArgument(astCall, argument, freshVariables, receiver, parameters, returnType)

        freshVariables.forEach(csBuilder::registerVariable)
        csBuilder.addSubtypeConstraint(resolvedArgument.type, expectedType, ArgumentConstraintPosition(argument))
        csBuilder.addLambdaArgument(resolvedArgument)

        return null
    }

    fun processCallableReferenceArgument(
            callContext: CallContext,
            csBuilder: ConstraintSystemBuilder,
            argument: CallableReferenceArgument,
            expectedType: UnwrappedType
    ): CallDiagnostic? {
        val position = ArgumentConstraintPosition(argument)

        if (argument !is ChosenCallableReferenceDescriptor) {
            val lhsType = argument.lhsType
            if (lhsType != null) {
                // todo: case with two receivers
                val expectedReceiverType = expectedType.supertypes().firstOrNull { it.isFunctionType }?.arguments?.first()?.type?.unwrap()
                if (expectedReceiverType != null) {
                    // (lhsType) -> .. <: (expectedReceiverType) -> ... => expectedReceiverType <: lhsType
                    csBuilder.addSubtypeConstraint(expectedReceiverType, lhsType, position)
                }
            }

            return null
        }

        val descriptor = argument.candidate.descriptor
        when (descriptor) {
            is FunctionDescriptor -> {
                // todo store resolved
                val resolvedFunctionReference = callContext.c.callableReferenceResolver.resolveFunctionReference(
                        argument, callContext.astCall, expectedType)

                csBuilder.addSubtypeConstraint(resolvedFunctionReference.reflectionType, expectedType, position)
                return resolvedFunctionReference.argumentsMapping?.diagnostics?.let {
                    ErrorCallableMapping(resolvedFunctionReference)
                }
            }
            is PropertyDescriptor -> {

                // todo store resolved
                val resolvedPropertyReference = callContext.c.callableReferenceResolver.resolvePropertyReference(descriptor,
                        argument, callContext.astCall, callContext.scopeTower.lexicalScope.ownerDescriptor)
                csBuilder.addSubtypeConstraint(resolvedPropertyReference.reflectionType, expectedType, position)
            }
            else -> throw UnsupportedOperationException("Callable reference resolved to an unsupported descriptor: $descriptor")
        }
        return null
    }
}

internal fun checkExpressionArgument(
        csBuilder: ConstraintSystemBuilder,
        expressionArgument: ExpressionArgument,
        expectedType: UnwrappedType,
        isReceiver: Boolean
): CallDiagnostic? {
    fun unstableSmartCastOrSubtypeError(
            unstableType: UnwrappedType?, expectedType: UnwrappedType, position: ArgumentConstraintPosition
    ): CallDiagnostic? {
        if (unstableType != null) {
            if (csBuilder.addIfIsCompatibleSubtypeConstraint(unstableType, expectedType, position)) {
                return UnstableSmartCast(expressionArgument, unstableType)
            }
        }
        csBuilder.addSubtypeConstraint(expressionArgument.type, expectedType, position)
        return null
    }

    val expectedNullableType = expectedType.makeNullableAsSpecified(true)
    val position = ArgumentConstraintPosition(expressionArgument)
    if (expressionArgument.isSafeCall) {
        if (!csBuilder.addIfIsCompatibleSubtypeConstraint(expressionArgument.type, expectedNullableType, position)) {
            return unstableSmartCastOrSubtypeError(expressionArgument.unstableType, expectedNullableType, position)?.let { return it }
        }
        return null
    }

    if (!csBuilder.addIfIsCompatibleSubtypeConstraint(expressionArgument.type, expectedType, position)) {
        if (!isReceiver) {
            return unstableSmartCastOrSubtypeError(expressionArgument.unstableType, expectedType, position)?.let { return it }
        }

        val unstableType = expressionArgument.unstableType
        if (unstableType != null && csBuilder.addIfIsCompatibleSubtypeConstraint(unstableType, expectedType, position)) {
            return UnstableSmartCast(expressionArgument, unstableType)
        }
        else if (csBuilder.addIfIsCompatibleSubtypeConstraint(expressionArgument.type, expectedNullableType, position)) {
            return UnsafeCallError(expressionArgument)
        }
        else {
            csBuilder.addSubtypeConstraint(expressionArgument.type, expectedType, position)
            return null
        }
    }

    return null
}

internal fun checkSubCallArgument(
        csBuilder: ConstraintSystemBuilder,
        subCallArgument: SubCallArgument,
        expectedType: UnwrappedType,
        isReceiver: Boolean
): CallDiagnostic? {
    val resolvedCall = subCallArgument.resolvedCall
    val expectedNullableType = expectedType.makeNullableAsSpecified(true)
    val position = ArgumentConstraintPosition(subCallArgument)

    csBuilder.addInnerCall(resolvedCall)

    if (subCallArgument.isSafeCall) {
        csBuilder.addSubtypeConstraint(resolvedCall.currentReturnType, expectedNullableType, position)
        return null
    }

    if (isReceiver && !csBuilder.addIfIsCompatibleSubtypeConstraint(resolvedCall.currentReturnType, expectedType, position) &&
        csBuilder.addIfIsCompatibleSubtypeConstraint(resolvedCall.currentReturnType, expectedNullableType, position)
    ) {
        return UnsafeCallError(subCallArgument)
    }

    csBuilder.addSubtypeConstraint(resolvedCall.currentReturnType, expectedType, position)
    return null
}

internal fun CallArgument.getExpectedType(parameter: ValueParameterDescriptor) =
        if (this.isSpread) {
            parameter.type.unwrap()
        }
        else {
            parameter.varargElementType?.unwrap() ?: parameter.type.unwrap()
        }