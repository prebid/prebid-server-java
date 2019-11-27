package org.prebid.server.privacy.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class Privacy {

    private static final Privacy EMPTY = Privacy.of(null, null, null);

    private final String gdpr;

    private final String consent;

    private final String ccpa;

    public static Privacy empty() {
        return EMPTY;
    }
}
