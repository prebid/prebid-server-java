package org.prebid.server.privacy.model;

import lombok.Value;
import org.prebid.server.privacy.ccpa.Ccpa;

@Value(staticConstructor = "of")
public class Privacy {

    String gdpr;

    String consentString;

    Ccpa ccpa;

    Integer coppa;

    public Privacy withoutConsent() {
        return Privacy.of(gdpr, "", ccpa, coppa);
    }
}
