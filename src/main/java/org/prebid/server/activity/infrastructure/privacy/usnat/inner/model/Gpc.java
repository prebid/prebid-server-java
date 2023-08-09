package org.prebid.server.activity.infrastructure.privacy.usnat.inner.model;

public enum Gpc implements USNatField<Boolean> {

    FALSE, TRUE;

    @Override
    public Boolean value() {
        return this == TRUE;
    }
}
