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

package org.jetbrains.kotlin.resolve.calls.components

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.tower.ResolutionCandidateApplicability.*
import org.jetbrains.kotlin.resolve.descriptorUtil.hasDefaultValue
import java.util.*

class ArgumentsToParametersMapper {

    data class ArgumentMapping(
            val parameterToCallArgumentMap: Map<ValueParameterDescriptor, ResolvedCallArgument>,
            val diagnostics: List<CallDiagnostic>
    )

    val EmptyArgumentMapping = ArgumentMapping(emptyMap(), emptyList())

    fun mapArguments(call: ASTCall, descriptor: CallableDescriptor): ArgumentMapping =
            mapArguments(call.argumentsInParenthesis, call.externalArgument, descriptor)

    fun mapArguments(
            argumentsInParenthesis: List<CallArgument>,
            externalArgument: CallArgument?,
            descriptor: CallableDescriptor
    ): ArgumentMapping {
        // optimization for case of variable
        if (argumentsInParenthesis.isEmpty() && externalArgument == null && descriptor.valueParameters.isEmpty()) {
            return EmptyArgumentMapping
        }
        else {
            val processor = CallArgumentProcessor(descriptor)
            processor.processArgumentsInParenthesis(argumentsInParenthesis)

            if (externalArgument != null) {
                processor.processExternalArgument(externalArgument)
            }
            processor.processDefaultsAndRunChecks()

            return ArgumentMapping(processor.result, processor.getDiagnostics())
        }
    }

    private class CallArgumentProcessor(val descriptor: CallableDescriptor) {
        val result: MutableMap<ValueParameterDescriptor, ResolvedCallArgument> = HashMap()
        private var state = State.POSITION_ARGUMENTS

        private val parameters: List<ValueParameterDescriptor> get() = descriptor.valueParameters

        private var diagnostics: MutableList<CallDiagnostic>? = null
        private var nameToParameter: Map<Name, ValueParameterDescriptor>? = null
        private var varargArguments: MutableList<CallArgument>? = null

        private var currentParameterIndex = 0

        private fun addDiagnostic(diagnostic: CallDiagnostic) {
            if (diagnostics == null) {
                diagnostics = ArrayList()
            }
            diagnostics!!.add(diagnostic)
        }

        fun getDiagnostics() = diagnostics ?: emptyList<CallDiagnostic>()

        private fun getParameterByName(name: Name): ValueParameterDescriptor? {
            if (nameToParameter == null) {
                nameToParameter = parameters.associateBy { it.name }
            }
            return nameToParameter!![name]
        }

        private fun addVarargArgument(argument: CallArgument) {
            if (varargArguments == null) {
                varargArguments = ArrayList()
            }
            varargArguments!!.add(argument)
        }

        private enum class State {
            POSITION_ARGUMENTS,
            VARARG_POSITION,
            NAMED_ARGUMENT
        }

        private fun completeVarargPositionArguments() {
            assert(state == State.VARARG_POSITION) { "Incorrect state: $state" }
            val parameter = parameters[currentParameterIndex]
            result.put(parameter.original, ResolvedCallArgument.VarargArgument(varargArguments!!))
        }

        // return true, if it was mapped to vararg parameter
        private fun processPositionArgument(argument: CallArgument): Boolean {
            if (state == State.NAMED_ARGUMENT) {
                addDiagnostic(MixingNamedAndPositionArguments(argument))
                return false
            }

            val parameter = parameters.getOrNull(currentParameterIndex)
            if (parameter == null) {
                addDiagnostic(ToManyArguments(argument, descriptor))
                return false
            }

            if (!parameter.isVararg) {
                currentParameterIndex++

                result.put(parameter.original, ResolvedCallArgument.SimpleArgument(argument))
                return false
            }
            // all position arguments will be mapped to current vararg parameter
            else {
                addVarargArgument(argument)
                return true
            }
        }

        private fun processNamedArgument(argument: CallArgument, name: Name) {
            if (!descriptor.hasStableParameterNames()) {
                addDiagnostic(NamedArgumentNotAllowed(argument, descriptor))
            }

            val parameter = findParameterByName(argument, name) ?: return

            addDiagnostic(NamedArgumentReference(argument, parameter))

            result[parameter.original]?.let {
                addDiagnostic(ArgumentPassedTwice(argument, parameter, it))
                return
            }

            result[parameter.original] = ResolvedCallArgument.SimpleArgument(argument)
        }

        private fun findParameterByName(argument: CallArgument, name: Name): ValueParameterDescriptor? {
            val parameter = getParameterByName(name)

            if (descriptor is CallableMemberDescriptor && descriptor.kind == CallableMemberDescriptor.Kind.FAKE_OVERRIDE) {
                if (parameter == null) {
                    for (valueParameter in descriptor.valueParameters) {
                        val matchedParameter = valueParameter.overriddenDescriptors.firstOrNull {
                            it.containingDeclaration.hasStableParameterNames() && it.name == name
                        }
                        if (matchedParameter != null) {
                            addDiagnostic(NamedArgumentReference(argument, valueParameter))
                            addDiagnostic(NameForAmbiguousParameter(argument, valueParameter, matchedParameter))
                            return matchedParameter
                        }
                    }
                }
                else {
                    parameter.getOverriddenParameterWithOtherName()?.let {
                        addDiagnostic(NameForAmbiguousParameter(argument, parameter, it))
                    }
                }
            }

            if (parameter == null) addDiagnostic(NameNotFound(argument, descriptor))

            return parameter
        }


        fun processArgumentsInParenthesis(arguments: List<CallArgument>) {
            for (argument in arguments) {
                val argumentName = argument.argumentName

                // process position argument
                if (argumentName == null) {
                    if (processPositionArgument(argument)) {
                        state = State.VARARG_POSITION
                    }
                }
                // process named argument
                else {
                    if (state == State.VARARG_POSITION) {
                        completeVarargPositionArguments()
                    }
                    state = State.POSITION_ARGUMENTS

                    processNamedArgument(argument, argumentName)
                }
            }
            if (state == State.VARARG_POSITION) {
                completeVarargPositionArguments()
            }
        }
        
        fun processExternalArgument(externalArgument: CallArgument) {
            val lastParameter = parameters.lastOrNull()
            if (lastParameter == null) {
                addDiagnostic(ToManyArguments(externalArgument, descriptor))
                return
            }

            val previousOccurrence = result[lastParameter.original]
            if (previousOccurrence != null) {
                addDiagnostic(ArgumentPassedTwice(externalArgument, lastParameter, previousOccurrence))
                return
            }

            if (lastParameter.isVararg) {
                addDiagnostic(VarargArgumentOutsideParentheses(externalArgument, lastParameter))
                return
            }

            result[lastParameter.original] = ResolvedCallArgument.SimpleArgument(externalArgument)
        }

        fun processDefaultsAndRunChecks() {
            for ((parameter, resolvedArgument) in result) {
                if (!parameter.isVararg) {
                    if (resolvedArgument !is ResolvedCallArgument.SimpleArgument) {
                        error("Incorrect resolved argument for parameter $parameter :$resolvedArgument")
                    }
                    else {
                        if (resolvedArgument.callArgument.isSpread) {
                            addDiagnostic(NonVarargSpread(resolvedArgument.callArgument, parameter))
                        }
                    }
                }
            }

            for (parameter in parameters) {
                if (!result.containsKey(parameter.original)) {
                    if (parameter.hasDefaultValue()) {
                        result[parameter.original] = ResolvedCallArgument.DefaultArgument
                    }
                    else if (parameter.isVararg) {
                        result[parameter.original] = ResolvedCallArgument.VarargArgument(emptyList())
                    }
                    else {
                        addDiagnostic(NoValueForParameter(parameter, descriptor))
                    }
                }
            }
        }
    }

}

class ToManyArguments(val argument: CallArgument, val descriptor: CallableDescriptor) :
        CallDiagnostic(INAPPLICABLE) {
    override fun report(reporter: DiagnosticReporter) = reporter.onCallArgument(argument, this)
}

class NonVarargSpread (val argument: CallArgument, val parameterDescriptor: ValueParameterDescriptor) :
        CallDiagnostic(INAPPLICABLE) {
    override fun report(reporter: DiagnosticReporter) = reporter.onCallArgumentSpread(argument, this)
}

class MixingNamedAndPositionArguments(val argument: CallArgument) :
        CallDiagnostic(INAPPLICABLE) {
    override fun report(reporter: DiagnosticReporter) = reporter.onCallArgument(argument, this)
}

class NamedArgumentNotAllowed(val argument: CallArgument, val descriptor: CallableDescriptor) :
        CallDiagnostic(INAPPLICABLE) {
    override fun report(reporter: DiagnosticReporter) = reporter.onCallArgumentName(argument, this)
}

class NameNotFound(val argument: CallArgument, val descriptor: CallableDescriptor) :
        CallDiagnostic(INAPPLICABLE) {
    override fun report(reporter: DiagnosticReporter) = reporter.onCallArgumentName(argument, this)
}

class NoValueForParameter(val parameterDescriptor: ValueParameterDescriptor,
                          val descriptor: CallableDescriptor) :
        CallDiagnostic(INAPPLICABLE) {
    override fun report(reporter: DiagnosticReporter) = reporter.onCall(this)
}

class ArgumentPassedTwice(val argument: CallArgument,
                          val parameterDescriptor: ValueParameterDescriptor,
                          val firstOccurrence: ResolvedCallArgument) :
        CallDiagnostic(INAPPLICABLE) {
    override fun report(reporter: DiagnosticReporter) = reporter.onCallArgumentName(argument, this)
}

class VarargArgumentOutsideParentheses(
        val argument: CallArgument,
        val parameterDescriptor: ValueParameterDescriptor) :
        CallDiagnostic(INAPPLICABLE) {
    override fun report(reporter: DiagnosticReporter) = reporter.onCallArgument(argument, this)
}

class NameForAmbiguousParameter(
        val argument: CallArgument,
        val parameterDescriptor: ValueParameterDescriptor,
        val overriddenParameterWithOtherName: ValueParameterDescriptor
) : CallDiagnostic(CONVENTION_ERROR) {
    override fun report(reporter: DiagnosticReporter) = reporter.onCallArgumentName(argument, this)
}

class NamedArgumentReference(
        val argument: CallArgument,
        val parameterDescriptor: ValueParameterDescriptor
) : CallDiagnostic(RESOLVED) {
    override fun report(reporter: DiagnosticReporter) = reporter.onCallArgumentName(argument, this)
}

val ValueParameterDescriptor.isVararg: Boolean get() = varargElementType != null

fun ValueParameterDescriptor.getOverriddenParameterWithOtherName() = overriddenDescriptors.firstOrNull {
    it.containingDeclaration.hasStableParameterNames() && it.name != name
}