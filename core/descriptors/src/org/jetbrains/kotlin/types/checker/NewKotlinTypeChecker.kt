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

package org.jetbrains.kotlin.types.checker

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.resolve.calls.inference.CapturedType
import org.jetbrains.kotlin.resolve.constants.IntegerValueTypeConstructor
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.checker.TypeCheckerSettings.SupertypesPolicy
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection
import org.jetbrains.kotlin.types.typeUtil.makeNullable
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.kotlin.utils.addToStdlib.check

object TypeStrictEqualityChecker {
    /**
     * String! != String & A<String!> != A<String>, also A<in Nothing> != A<out Any?>
     * also A<*> != A<out Any?>
     * different error types non-equals even errorTypeEqualToAnything
     */
    fun strictEqualTypes(a: UnwrappedType, b: UnwrappedType): Boolean {
        if (a === b) return true

        if (a is SimpleType && b is SimpleType) return strictEqualTypes(a, b)
        if (a is FlexibleType && b is FlexibleType) {
            return strictEqualTypes(a.lowerBound, b.lowerBound) &&
                   strictEqualTypes(a.upperBound, b.upperBound)
        }
        return false
    }

    fun strictEqualTypes(a: SimpleType, b: SimpleType): Boolean {
        if (a.isMarkedNullable != b.isMarkedNullable
            || a.constructor != b.constructor
            || a.arguments.size != b.arguments.size
        ) {
            return false
        }
        for (i in a.arguments.indices) {
            val aArg = a.arguments[i]
            val bArg = b.arguments[i]
            if (aArg.isStarProjection != bArg.isStarProjection) return false

            // both non-star
            if (!aArg.isStarProjection) {
                if (aArg.projectionKind != bArg.projectionKind) return false
                if (!strictEqualTypes(aArg.type.unwrap(), bArg.type.unwrap())) return false
            }
        }
        return true
    }

}

object NewKotlinTypeChecker {

    fun TypeCheckerSettings.equalTypes(a: UnwrappedType, b: UnwrappedType): Boolean {
        if (a === b) return true

        return isSubtypeOf(a, b) && isSubtypeOf(b, a)
    }

    fun TypeCheckerSettings.isSubtypeOf(subType: UnwrappedType, superType: UnwrappedType) =
        isSubtypeOf(subType.lowerIfFlexible(), superType.upperIfFlexible())

    fun TypeCheckerSettings.isSubtypeOf(subType: SimpleType, superType: SimpleType): Boolean {
        fun transformToNewType(type: SimpleType): SimpleType {
            if (type is CapturedType) {
                val lowerType = type.typeProjection.check { it.projectionKind == Variance.IN_VARIANCE }?.type?.unwrap()

                val newCapturedType = NewCapturedType(CaptureStatus.FOR_SUBTYPING, type.constructor.newTypeConstructor,
                                                      lowerType, type.annotations, type.isMarkedNullable)
                return newCapturedType
            }

            if (type.constructor is IntersectionTypeConstructor && type.isMarkedNullable) {
                val newSuperTypes = type.constructor.supertypes.map { it.makeNullable() }
                val newConstructor = IntersectionTypeConstructor(newSuperTypes)
                return KotlinTypeFactory.simpleType(type.annotations, newConstructor, listOf(), false, newConstructor.createScopeForKotlinType())
            }

            if (type.constructor is IntegerValueTypeConstructor) {
                val newConstructor = IntersectionTypeConstructor(type.constructor.supertypes.map { TypeUtils.makeNullableAsSpecified(it, type.isMarkedNullable) })
                return KotlinTypeFactory.simpleType(type.annotations, newConstructor, listOf(), false, type.memberScope)
            }

            return type
        }

        return isSubtypeOfForNewTypes(transformToNewType(subType), transformToNewType(superType))
    }

    private fun TypeCheckerSettings.isSubtypeOfForNewTypes(subType: SimpleType, superType: SimpleType): Boolean {
        if (isSubtypeByExternalRule(subType, superType)) return true // todo: do not call this for T? <: String?

        if (subType.isError || superType.isError) {
            if (errorTypeEqualsToAnything) return true

            if (subType.isMarkedNullable && !superType.isMarkedNullable) return false

            return TypeStrictEqualityChecker.strictEqualTypes(subType.makeNullableAsSpecified(false), superType.makeNullableAsSpecified(false))
        }

        if (superType is NewCapturedType && superType.lowerType != null && isSubtypeOf(subType, superType.lowerType)) return true

        (superType.constructor as? IntersectionTypeConstructor)?.let {
            assert(!superType.isMarkedNullable) { "Intersection type should not be marked nullable!: $superType" }
            return it.supertypes.all { isSubtypeOf(subType, it.unwrap()) }
        }

        return isSubtypeOfForSingleClassifierType(subType, superType)
    }

    private fun TypeCheckerSettings.isSubtypeOfForSingleClassifierType(subType: SimpleType, superType: SimpleType): Boolean {
        assert(subType.isSingleClassifierType || subType.isIntersectionType) { "Not singleClassifierType and not intersection subType: $subType" }
        assert(superType.isSingleClassifierType) { "Not singleClassifierType superType: $superType" }

        if (!NullabilityChecker.isPossibleSubtype(this, subType, superType)) return false

        if (KotlinBuiltIns.isNothingOrNullableNothing(subType)) return true

        val superConstructor = superType.constructor
        val supertypesWithSameConstructor = collectAllSupertypesForTypeConstructor(subType, superConstructor)
        when (supertypesWithSameConstructor.size) {
            0 -> return false
            1 -> return isSubtypeForSameConstructor(supertypesWithSameConstructor.first().arguments, superType)

            else -> { // at least 2 supertypes with same constructors. Such case is rare
                if (supertypesWithSameConstructor.any { isSubtypeForSameConstructor(it.arguments, superType) }) return true

                val newArguments = superConstructor.parameters.mapIndexed { index, parameterDescriptor ->
                    val allProjections = supertypesWithSameConstructor.map {
                        it.arguments.getOrNull(index)?.check { it.projectionKind == Variance.INVARIANT }?.type?.unwrap()
                        ?: error("Incorrect type: $it, subType: $subType, superType: $superType")
                    }

                    // todo discuss
                    intersectTypes(allProjections).asTypeProjection()
                }

                return isSubtypeForSameConstructor(newArguments, superType)
            }
        }
    }

    // nullability was checked earlier via nullabilityChecker
    private fun TypeCheckerSettings.collectAllSupertypesForTypeConstructor(
            baseType: SimpleType,
            constructor: TypeConstructor
    ): List<SimpleType> {
        val result = SmartList<SimpleType>()

        anySupertype(captureFromArguments(baseType, CaptureStatus.FOR_SUBTYPING), { false }) {
            val current = captureFromArguments(it, CaptureStatus.FOR_SUBTYPING)

            if (current.constructor == constructor) {
                result.add(current)
                return@anySupertype SupertypesPolicy.NONE
            }

            val substitution = TypeConstructorSubstitution.create(current).buildSubstitutor()
            if (substitution.isEmpty) {
                SupertypesPolicy.LOWER_IF_FLEXIBLE
            }
            else {
                SupertypesPolicy.LowerIfFlexibleWithCustomSubstitutor(substitution)
            }
        }

        // see test javaAndKotlinSuperType.kt
        if (baseType.isClassType && result.size > 1) {
            return result.subList(0, 1)
        }

        return result
    }

    private fun effectiveVariance(declared: Variance, useSide: Variance): Variance? {
        if (declared == Variance.INVARIANT) return useSide
        if (useSide == Variance.INVARIANT) return declared

        // both not INVARIANT
        if (declared == useSide) return declared

        // composite In with Out
        return null
    }

    private fun TypeCheckerSettings.isSubtypeForSameConstructor(
            capturedSubArguments: List<TypeProjection>,
            superType: SimpleType
    ): Boolean {
        val parameters = superType.constructor.parameters

        for (index in parameters.indices) {
            val superProjection = superType.arguments[index]
            if (superProjection.isStarProjection) continue // A<B> <: A<*>

            val superArgumentType = superProjection.type.unwrap()
            val subArgumentType = capturedSubArguments[index].let {
                assert(it.projectionKind == Variance.INVARIANT) { "Incorrect sub argument: $it" }
                it.type.unwrap()
            }

            val variance = effectiveVariance(parameters[index].variance, superProjection.projectionKind)
                           ?: return errorTypeEqualsToAnything

            val correctArgument = runWithArgumentsSettings(subArgumentType) {
                when (variance) {
                    Variance.INVARIANT -> equalTypes(subArgumentType, superArgumentType)
                    Variance.OUT_VARIANCE -> isSubtypeOf(subArgumentType, superArgumentType)
                    Variance.IN_VARIANCE -> isSubtypeOf(superArgumentType, subArgumentType)
                }
            }
            if (!correctArgument) return false
        }
        return true
    }

}


object NullabilityChecker {

    // this method checks only nullability
    fun isPossibleSubtype(settings: TypeCheckerSettings, subType: SimpleType, superType: SimpleType): Boolean =
            settings.runIsPossibleSubtype(subType, superType)

    private fun TypeCheckerSettings.runIsPossibleSubtype(subType: SimpleType, superType: SimpleType): Boolean {
        // it makes for case String? & Any <: String
        assert(subType.isIntersectionType || subType.isSingleClassifierType) {"Not singleClassifierType superType: $superType"}
        assert(superType.isSingleClassifierType) {"Not singleClassifierType superType: $superType"}

        // i.e. subType is not-nullable
        if (hasNotNullSupertype(subType, SupertypesPolicy.LOWER_IF_FLEXIBLE)) return true

        // i.e subType hasn't not-null supertype, but superType has
        if (hasNotNullSupertype(superType, SupertypesPolicy.UPPER_IF_FLEXIBLE)) return false

        // both superType and subType hasn't not-null supertype.

        // superType is actually nullable
        if (superType.isMarkedNullable) return true

        /**
         * If we still don't know, it means, that superType is not classType, for example -- type parameter.
         *
         * For captured types with lower bound this function can give to you false result. Example:
         *  class A<T>, A<in Number> => \exist Q : Number <: Q. A<Q>
         *      isPossibleSubtype(Number, Q) = false.
         *      Such cases should be taken in to account in [NewKotlinTypeChecker.isSubtypeOf] (same for intersection types)
         */

        // classType cannot has special type in supertype list
        if (subType.isClassType) return false

        return hasPathByNotMarkedNullableNodes(subType, superType.constructor)
    }

    private fun TypeCheckerSettings.hasNotNullSupertype(type: SimpleType, supertypesPolicy: SupertypesPolicy) =
            anySupertype(type, { it.isClassType && !it.isMarkedNullable }) {
                if (it.isMarkedNullable) SupertypesPolicy.NONE else supertypesPolicy
            }

    private fun TypeCheckerSettings.hasPathByNotMarkedNullableNodes(start: SimpleType, end: TypeConstructor) =
            anySupertype(start, { !it.isMarkedNullable && it.constructor == end }) {
                if (it.isMarkedNullable) SupertypesPolicy.NONE else SupertypesPolicy.LOWER_IF_FLEXIBLE
            }

}

/**
 * ClassType means that type constructor for this type is type for real class or interface
 */
private val SimpleType.isClassType: Boolean get() = constructor.declarationDescriptor is ClassDescriptor

/**
 * SingleClassifierType is one of the following types:
 *  - classType
 *  - type for type parameter
 *  - captured type
 *
 * Such types can contains error types in our arguments, but type constructor isn't errorTypeConstructor
 */
private val SimpleType.isSingleClassifierType: Boolean
    get() = !isError &&
            (constructor.declarationDescriptor != null || this is CapturedType || this is NewCapturedType)

private val SimpleType.isIntersectionType: Boolean
    get() = constructor is IntersectionTypeConstructor