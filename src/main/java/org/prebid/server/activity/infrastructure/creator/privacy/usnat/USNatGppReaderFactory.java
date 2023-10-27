package org.prebid.server.activity.infrastructure.creator.privacy.usnat;

import com.iab.gpp.encoder.GppModel;
import org.prebid.server.activity.infrastructure.privacy.PrivacySection;
import org.prebid.server.activity.infrastructure.privacy.usnat.USNatGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USMappedCaliforniaGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USMappedColoradoGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USMappedConnecticutGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USMappedUtahGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USMappedVirginiaGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USNationalGppReader;

public class USNatGppReaderFactory {

    public USNatGppReader forSection(int sectionId, GppModel gppModel) {
        return switch (PrivacySection.from(sectionId)) {
            case NATIONAL -> new USNationalGppReader(gppModel);
            case CALIFORNIA -> new USMappedCaliforniaGppReader(gppModel);
            case VIRGINIA -> new USMappedVirginiaGppReader(gppModel);
            case COLORADO -> new USMappedColoradoGppReader(gppModel);
            case UTAH -> new USMappedUtahGppReader(gppModel);
            case CONNECTICUT -> new USMappedConnecticutGppReader(gppModel);
        };
    }
}
