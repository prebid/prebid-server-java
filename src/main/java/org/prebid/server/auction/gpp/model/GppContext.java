package org.prebid.server.auction.gpp.model;

import com.iab.gpp.encoder.GppModel;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Set;

@Value(staticConstructor = "of")
public class GppContext {

    Scope scope;

    Regions regions;

    List<String> errors;

    public GppContext with(Regions regions) {
        return GppContext.of(scope, regions, errors);
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

        @Value(staticConstructor = "of")
        public static class TcfEuV2Privacy {

            Integer gdpr;

            String consent;
        }

        @Value(staticConstructor = "of")
        public static class UspV1Privacy {

            String usPrivacy;
        }
    }
}
