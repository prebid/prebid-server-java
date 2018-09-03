package org.prebid.server.validation.model;

import com.iab.openrtb.request.Imp;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Class defined validation result.
 */
@AllArgsConstructor
@Value
public class ValidationResult {

    /**
     * Critical error message that stops validation process which means that checked object is invalid.
     */
    String failedError;

    /**
     * Invalid {@link Imp}s with validation message.
     */
    Map<Imp, String> impToError;

    /**
     * Checks if validation was stopped and object was defined as invalid.
     */
    public boolean hasFailed() {
        return StringUtils.isNotEmpty(failedError);
    }

    /**
     * Creates {@link ValidationResult} with single critical error.
     */
    public static ValidationResult error(String errorMessageFormat, Object... args) {
        return new ValidationResult(String.format(errorMessageFormat, args), null);
    }

    /**
     * Creates {@link ValidationResult} with critical error, list of warnings and invalid {@link List<Imp>}.
     */
    public static ValidationResult requestValidationError(String criticalError,
                                                          Map<Imp, String> invalidImps) {
        return new ValidationResult(criticalError, invalidImps);
    }

    /**
     * Creates {@link ValidationResult} with null failed error and map imps to its validation errors.
     */
    public static ValidationResult successWithImpErrors(Map<Imp, String> impToError) {
        return new ValidationResult(null, impToError);
    }

    /**
     * Creates {@link ValidationResult} without errors.
     */
    public static ValidationResult success() {
        return new ValidationResult(null, Collections.emptyMap());
    }
}
