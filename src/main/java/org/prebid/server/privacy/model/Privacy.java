package org.prebid.server.privacy.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.privacy.ccpa.Ccpa;

@AllArgsConstructor(staticName = "of")
@Value
public final class Privacy {

    private static final Privacy EMPTY = Privacy.of(null, null, Ccpa.EMPTY);

    private final String gdpr;

    private final String consent;

    private final Ccpa ccpa;

    public static Privacy empty() {
        return EMPTY;
    }
}

