package org.prebid.server.privacy.model;

import lombok.Value;
import org.apache.commons.lang3.ObjectUtils;

@Value
public final class Privacy {

    private static final String DEFAULT_CONSENT_VALUE = "";
    private static final String DEFAULT_GDPR_VALUE = "";
    private static final String DEFAULT_CCPA_VALUE = "";

    private static final Privacy EMPTY = Privacy.of(DEFAULT_GDPR_VALUE, DEFAULT_CONSENT_VALUE, DEFAULT_CCPA_VALUE);

    private final String gdpr;

    private final String consent;

    private final String ccpa;

    private Privacy(String gdpr, String consent, String ccpa) {
        this.gdpr = gdpr;
        this.consent = consent;
        this.ccpa = ccpa;
    }

    public static Privacy of(String gdpr, String consent, String ccpa) {
        final String validatedGdpr = ObjectUtils.notEqual(gdpr, "1") && ObjectUtils.notEqual(gdpr, "0")
                ? Privacy.DEFAULT_GDPR_VALUE
                : gdpr;
        final String validatedConsent = consent == null ? DEFAULT_CONSENT_VALUE : consent;
        final String validatedCCPA = ccpa == null ? DEFAULT_CCPA_VALUE : ccpa;
        return new Privacy(validatedGdpr, validatedConsent, validatedCCPA);
    }

    public static Privacy empty() {
        return EMPTY;
    }
}
