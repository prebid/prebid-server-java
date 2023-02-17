package org.prebid.server.auction.gpp.model;

import com.iab.gpp.encoder.GppModel;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.auction.gpp.model.privacy.Privacy;
import org.prebid.server.auction.gpp.model.privacy.TcfEuV2Privacy;
import org.prebid.server.auction.gpp.model.privacy.UspV1Privacy;

import java.util.List;
import java.util.Set;

@Value
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class GppContext {

    Scope scope;

    Regions regions;

    List<String> errors;

    public GppContext with(Privacy privacy) {
        return new GppContext(scope, GppContextUtils.withPrivacy(regions, privacy), errors);
    }

    @Value(staticConstructor = "of")
    public static class Scope {

        GppModel gppModel;

        Set<Integer> sectionsIds;
    }

    @Builder(toBuilder = true)
    @Value
    public static class Regions {

        TcfEuV2Privacy tcfEuV2Privacy;

        UspV1Privacy uspV1Privacy;
    }
}
