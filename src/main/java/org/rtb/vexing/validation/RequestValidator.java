package org.rtb.vexing.validation;

import com.iab.openrtb.request.BidRequest;

import java.util.Collections;

public class RequestValidator {
    public RequestValidator() {
    }

    public ValidationResult validate(BidRequest bidRequest) {
        return new ValidationResult(Collections.emptyList());
    }
}
