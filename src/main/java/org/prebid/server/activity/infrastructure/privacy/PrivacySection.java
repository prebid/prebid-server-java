package org.prebid.server.activity.infrastructure.privacy;

import com.iab.gpp.encoder.section.UsCaV1;
import com.iab.gpp.encoder.section.UsCoV1;
import com.iab.gpp.encoder.section.UsCtV1;
import com.iab.gpp.encoder.section.UsNatV1;
import com.iab.gpp.encoder.section.UsUtV1;
import com.iab.gpp.encoder.section.UsVaV1;

import java.util.Set;

public enum PrivacySection {

    NATIONAL(UsNatV1.ID),
    CALIFORNIA(UsCaV1.ID),
    VIRGINIA(UsVaV1.ID),
    COLORADO(UsCoV1.ID),
    UTAH(UsUtV1.ID),
    CONNECTICUT(UsCtV1.ID);

    public static final Set<PrivacySection> US_PRIVACY_SECTIONS = Set.of(
            NATIONAL,
            CALIFORNIA,
            VIRGINIA,
            COLORADO,
            UTAH,
            CONNECTICUT);

    private final int sectionId;

    PrivacySection(int sectionId) {
        this.sectionId = sectionId;
    }

    public Integer sectionId() {
        return sectionId;
    }

    public static PrivacySection from(int sectionId) {
        final PrivacySection[] values = PrivacySection.values();
        if (sectionId < NATIONAL.sectionId || sectionId > CONNECTICUT.sectionId) {
            throw new IllegalArgumentException("US sectionId must be in [%s, %s]."
                    .formatted(NATIONAL.sectionId, CONNECTICUT.sectionId));
        }

        return values[sectionId - NATIONAL.sectionId];
    }
}
