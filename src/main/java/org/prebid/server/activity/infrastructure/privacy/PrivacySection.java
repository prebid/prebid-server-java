package org.prebid.server.activity.infrastructure.privacy;

import com.iab.gpp.encoder.section.UsCa;
import com.iab.gpp.encoder.section.UsCo;
import com.iab.gpp.encoder.section.UsCt;
import com.iab.gpp.encoder.section.UsNat;
import com.iab.gpp.encoder.section.UsUt;
import com.iab.gpp.encoder.section.UsVa;

import java.util.Set;

public enum PrivacySection {

    NATIONAL(UsNat.ID),
    CALIFORNIA(UsCa.ID),
    VIRGINIA(UsVa.ID),
    COLORADO(UsCo.ID),
    UTAH(UsUt.ID),
    CONNECTICUT(UsCt.ID);

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
