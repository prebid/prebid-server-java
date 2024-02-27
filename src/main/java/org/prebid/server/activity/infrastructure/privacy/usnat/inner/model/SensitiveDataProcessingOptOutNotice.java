package org.prebid.server.activity.infrastructure.privacy.usnat.inner.model;

public enum SensitiveDataProcessingOptOutNotice implements USNatField<Integer> {

    NOT_APPLICABLE(0),
    YES(1),
    NO(2);

    private final int value;

    SensitiveDataProcessingOptOutNotice(int value) {
        this.value = value;
    }

    @Override
    public Integer value() {
        return value;
    }
}
