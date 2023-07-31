package org.prebid.server.activity.infrastructure.creator.privacy.usnat;

import com.iab.gpp.encoder.section.UspCaV1;
import com.iab.gpp.encoder.section.UspCoV1;
import com.iab.gpp.encoder.section.UspCtV1;
import com.iab.gpp.encoder.section.UspNatV1;
import com.iab.gpp.encoder.section.UspUtV1;
import com.iab.gpp.encoder.section.UspVaV1;

public enum USNatSection {

    NATIONAL(UspNatV1.ID),
    CALIFORNIA(UspCaV1.ID),
    VIRGINIA(UspVaV1.ID),
    COLORADO(UspCoV1.ID),
    UTAH(UspUtV1.ID),
    CONNECTICUT(UspCtV1.ID);

    private final int sectionId;

    USNatSection(int sectionId) {
        this.sectionId = sectionId;
    }

    public Integer sectionId() {
        return sectionId;
    }

    public static USNatSection from(int sectionId) {
        final USNatSection[] values = USNatSection.values();
        if (sectionId < NATIONAL.sectionId || sectionId > CONNECTICUT.sectionId) {
            throw new IllegalArgumentException("US sectionId must be in [%s, %s]."
                    .formatted(NATIONAL.sectionId, CONNECTICUT.sectionId));
        }

        return values[sectionId - NATIONAL.sectionId];
    }
}
