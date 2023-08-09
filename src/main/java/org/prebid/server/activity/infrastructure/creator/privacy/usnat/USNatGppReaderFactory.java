package org.prebid.server.activity.infrastructure.creator.privacy.usnat;

import com.iab.gpp.encoder.GppModel;
import org.prebid.server.activity.infrastructure.privacy.usnat.USNatGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USCaliforniaGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USColoradoGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USConnecticutGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USNationalGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USUtahGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USVirginiaGppReader;

public class USNatGppReaderFactory {

    public USNatGppReader forSection(Integer sectionId, GppModel gppModel) {
        return switch (USNatSection.from(sectionId)) {
            case NATIONAL -> new USNationalGppReader(gppModel);
            case CALIFORNIA -> new USCaliforniaGppReader(gppModel);
            case VIRGINIA -> new USVirginiaGppReader(gppModel);
            case COLORADO -> new USColoradoGppReader(gppModel);
            case UTAH -> new USUtahGppReader(gppModel);
            case CONNECTICUT -> new USConnecticutGppReader(gppModel);
        };
    }
}
