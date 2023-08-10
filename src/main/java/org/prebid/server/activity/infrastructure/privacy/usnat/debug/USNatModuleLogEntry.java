package org.prebid.server.activity.infrastructure.privacy.usnat.debug;

import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.activity.infrastructure.privacy.usnat.USNatGppReader;
import org.prebid.server.activity.infrastructure.rule.Rule;

public class USNatModuleLogEntry {

    public static String from(PrivacyModule privacyModule, USNatGppReader gppReader, Rule.Result precomputedResult) {
        return "%s with %s. Precomputed result: %s.".formatted(
                privacyModule.getClass().getSimpleName(),
                gppReader.getClass().getSimpleName(),
                precomputedResult);
    }

    public static String from(PrivacyModule privacyModule, Rule.Result precomputedResult) {
        return "%s. Precomputed result: %s.".formatted(
                privacyModule.getClass().getSimpleName(),
                precomputedResult);
    }
}
