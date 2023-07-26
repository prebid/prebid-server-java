package org.prebid.server.activity.infrastructure.creator.privacy.usnat;

public enum USNatSection {

    NATIONAL,
    CALIFORNIA,
    VIRGINIA,
    COLORADO,
    UTAH,
    CONNECTICUT;

    private static final int SHIFT = 7;

    public Integer sectionId() {
        return ordinal() + SHIFT;
    }

    public static USNatSection from(Integer sectionId) {
        return USNatSection.values()[sectionId - SHIFT];
    }
}
