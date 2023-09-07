package org.prebid.server.activity.infrastructure.privacy.usnat.inner.model;

public enum TargetedAdvertisingOptOut implements USNatField<Integer> {

    NOT_APPLICABLE(0),
    OPTED_OUT(1),
    DID_NOT_OPT_OUT(2);

    private final int value;

    TargetedAdvertisingOptOut(int value) {
        this.value = value;
    }

    @Override
    public Integer value() {
        return value;
    }
}
