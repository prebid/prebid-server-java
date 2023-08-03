package org.prebid.server.activity.infrastructure.privacy.usnat.inner.model;

public enum KnownChildSensitiveDataConsent implements USNatField<Integer> {

    NO_CONSENT(1),
    CONSENT(2);

    private final int value;

    KnownChildSensitiveDataConsent(int value) {
        this.value = value;
    }

    @Override
    public Integer value() {
        return value;
    }
}
