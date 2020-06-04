package org.prebid.server.privacy.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.privacy.ccpa.Ccpa;

@AllArgsConstructor(staticName = "of")
@Value
public final class Privacy {

    private final String gdpr;

    private final String consent;

    private final Ccpa ccpa;
}
