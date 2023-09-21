package org.prebid.server.activity.infrastructure.creator.privacy.uscustomlogic;

import com.iab.gpp.encoder.GppModel;
import org.prebid.server.activity.infrastructure.privacy.PrivacySection;
import org.prebid.server.activity.infrastructure.privacy.uscustomlogic.USCustomLogicGppReader;
import org.prebid.server.activity.infrastructure.privacy.uscustomlogic.reader.USCaliforniaGppReader;
import org.prebid.server.activity.infrastructure.privacy.uscustomlogic.reader.USColoradoGppReader;
import org.prebid.server.activity.infrastructure.privacy.uscustomlogic.reader.USConnecticutGppReader;
import org.prebid.server.activity.infrastructure.privacy.uscustomlogic.reader.USUtahGppReader;
import org.prebid.server.activity.infrastructure.privacy.uscustomlogic.reader.USVirginiaGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USMappedCaliforniaGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USMappedColoradoGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USMappedConnecticutGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USMappedUtahGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USMappedVirginiaGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USNationalGppReader;

public class USCustomLogicGppReaderFactory {

    public USCustomLogicGppReader forSection(int sectionId, boolean normalizeSection, GppModel gppModel) {
        final PrivacySection privacySection = PrivacySection.from(sectionId);
        return !normalizeSection ? originalReader(privacySection, gppModel) : mappedReader(privacySection, gppModel);
    }

    private static USCustomLogicGppReader originalReader(PrivacySection privacySection, GppModel gppModel) {
        return switch (privacySection) {
            case NATIONAL -> new USNationalGppReader(gppModel);
            case CALIFORNIA -> new USCaliforniaGppReader(gppModel);
            case VIRGINIA -> new USVirginiaGppReader(gppModel);
            case COLORADO -> new USColoradoGppReader(gppModel);
            case UTAH -> new USUtahGppReader(gppModel);
            case CONNECTICUT -> new USConnecticutGppReader(gppModel);
        };
    }

    private static USCustomLogicGppReader mappedReader(PrivacySection privacySection, GppModel gppModel) {
        return switch (privacySection) {
            case NATIONAL -> new USNationalGppReader(gppModel);
            case CALIFORNIA -> new USMappedCaliforniaGppReader(gppModel);
            case VIRGINIA -> new USMappedVirginiaGppReader(gppModel);
            case COLORADO -> new USMappedColoradoGppReader(gppModel);
            case UTAH -> new USMappedUtahGppReader(gppModel);
            case CONNECTICUT -> new USMappedConnecticutGppReader(gppModel);
        };
    }
}
