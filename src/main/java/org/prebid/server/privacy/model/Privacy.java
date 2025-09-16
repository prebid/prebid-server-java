package org.prebid.server.privacy.model;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.privacy.ccpa.Ccpa;

import java.util.List;

@Value
@Builder(toBuilder = true)
public class Privacy {

    String gdpr;

    String consentString;

    Ccpa ccpa;

    Integer coppa;

    String gpp;

    List<Integer> gppSid;

    public Privacy withoutConsent() {
        return toBuilder().consentString("").build();
    }
}
