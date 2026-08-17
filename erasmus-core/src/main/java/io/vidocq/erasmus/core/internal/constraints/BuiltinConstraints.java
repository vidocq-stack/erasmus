/*
 * Copyright (c) 2026 Yann Blazart, Antoine Sabot-Durand and the Vidocq contributors
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * or any later version, which is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * It is also made available under the European Union Public Licence v. 1.2,
 * which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * SPDX-License-Identifier: EPL-2.0 OR EUPL-1.2 OR GPL-2.0-or-later
 */
package io.vidocq.erasmus.core.internal.constraints;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;

/**
 * Default validator classes for every built-in Jakarta Bean Validation constraint.
 *
 * <p>This registry exists because the spec's own built-in annotations declare
 * {@code @Constraint(validatedBy = {})} — an EMPTY array (verified against the
 * {@code jakarta.validation-api} class files: {@code @Constraint} meta-annotation
 * resolution only carries real classes for user-defined custom constraints, where
 * the user's own code names its own validator). Every compliant implementation
 * must therefore maintain its own default-validator mapping for the built-ins —
 * this is that mapping. See {@code ConstraintDescriptorImpl} for how it is
 * consulted alongside {@code @Constraint.validatedBy()}.
 */
public final class BuiltinConstraints {

    private static final Map<Class<? extends Annotation>, List<Class<? extends ConstraintValidator<?, ?>>>> VALIDATORS =
            Map.ofEntries(
                    Map.entry(NotNull.class, List.of(NotNullValidator.class)),
                    Map.entry(NotEmpty.class, List.of(
                            NotEmptyValidatorForCharSequence.class,
                            NotEmptyValidatorForCollection.class,
                            NotEmptyValidatorForMap.class,
                            NotEmptyValidatorForArray.class)),
                    Map.entry(NotBlank.class, List.of(NotBlankValidator.class)),
                    Map.entry(Size.class, List.of(
                            SizeValidatorForCharSequence.class,
                            SizeValidatorForCollection.class,
                            SizeValidatorForMap.class,
                            SizeValidatorForArray.class)),
                    Map.entry(Min.class, List.of(MinValidatorForNumber.class)),
                    Map.entry(Max.class, List.of(MaxValidatorForNumber.class)),
                    Map.entry(AssertTrue.class, List.of(AssertTrueValidator.class)),
                    Map.entry(AssertFalse.class, List.of(AssertFalseValidator.class)),
                    Map.entry(Positive.class, List.of(PositiveValidator.class)),
                    Map.entry(PositiveOrZero.class, List.of(PositiveOrZeroValidator.class)),
                    Map.entry(Negative.class, List.of(NegativeValidator.class)),
                    Map.entry(NegativeOrZero.class, List.of(NegativeOrZeroValidator.class)),
                    Map.entry(DecimalMin.class, List.of(
                            DecimalMinValidatorForNumber.class,
                            DecimalMinValidatorForCharSequence.class)),
                    Map.entry(DecimalMax.class, List.of(
                            DecimalMaxValidatorForNumber.class,
                            DecimalMaxValidatorForCharSequence.class)),
                    Map.entry(Digits.class, List.of(
                            DigitsValidatorForNumber.class,
                            DigitsValidatorForCharSequence.class)),
                    Map.entry(Pattern.class, List.of(PatternValidator.class)),
                    Map.entry(Email.class, List.of(EmailValidator.class)),
                    Map.entry(Past.class, List.of(
                            PastValidatorForInstant.class,
                            PastValidatorForLocalDate.class,
                            PastValidatorForDate.class)),
                    Map.entry(PastOrPresent.class, List.of(
                            PastOrPresentValidatorForInstant.class,
                            PastOrPresentValidatorForLocalDate.class,
                            PastOrPresentValidatorForDate.class)),
                    Map.entry(Future.class, List.of(
                            FutureValidatorForInstant.class,
                            FutureValidatorForLocalDate.class,
                            FutureValidatorForDate.class)),
                    Map.entry(FutureOrPresent.class, List.of(
                            FutureOrPresentValidatorForInstant.class,
                            FutureOrPresentValidatorForLocalDate.class,
                            FutureOrPresentValidatorForDate.class)));

    private BuiltinConstraints() {
    }

    /**
     * @return the default validator classes for {@code constraintType}, or {@code null}
     * if it is not a built-in constraint (the caller falls back to
     * {@code @Constraint.validatedBy()} for custom constraints).
     */
    public static List<Class<? extends ConstraintValidator<?, ?>>> validatorsFor(Class<? extends Annotation> constraintType) {
        return VALIDATORS.get(constraintType);
    }
}
