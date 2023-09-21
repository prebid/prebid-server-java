package org.prebid.server.activity.infrastructure.privacy;

import com.iab.gpp.encoder.section.UspCaV1;
import com.iab.gpp.encoder.section.UspCoV1;
import com.iab.gpp.encoder.section.UspCtV1;
import com.iab.gpp.encoder.section.UspNatV1;
import com.iab.gpp.encoder.section.UspUtV1;
import com.iab.gpp.encoder.section.UspVaV1;

import java.util.Set;

public enum PrivacySection {

    NATIONAL(UspNatV1.ID),
    CALIFORNIA(UspCaV1.ID),
    VIRGINIA(UspVaV1.ID),
    COLORADO(UspCoV1.ID),
    UTAH(UspUtV1.ID),
    CONNECTICUT(UspCtV1.ID);

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
