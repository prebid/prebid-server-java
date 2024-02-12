package org.prebid.server.activity.infrastructure.privacy.usnat.debug;

import com.fasterxml.jackson.databind.node.TextNode;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.activity.infrastructure.privacy.usnat.USNatGppReader;
import org.prebid.server.activity.infrastructure.rule.Rule;

public class USNatModuleLogEntry {

    private USNatModuleLogEntry() {
    }

    public static TextNode from(PrivacyModule privacyModule, USNatGppReader gppReader, Rule.Result precomputedResult) {
        return TextNode.valueOf(
                "%s with %s. Precomputed result: %s.".formatted(
                        privacyModule.getClass().getSimpleName(),
                        gppReader.getClass().getSimpleName(),
                        precomputedResult));
    }

    public static TextNode from(PrivacyModule privacyModule, Rule.Result precomputedResult) {
        return TextNode.valueOf(
                "%s. Precomputed result: %s.".formatted(
                        privacyModule.getClass().getSimpleName(),
                        precomputedResult));
    }
}
