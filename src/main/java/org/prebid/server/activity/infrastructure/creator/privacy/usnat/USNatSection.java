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
        final USNatSection[] values = USNatSection.values();
        if (sectionId < SHIFT || sectionId >= values.length + SHIFT) {
            throw new IllegalArgumentException("US sectionId must be in [%s, %s]."
                    .formatted(SHIFT, values.length + SHIFT - 1));
        }

        return values[sectionId - SHIFT];
    }
}
