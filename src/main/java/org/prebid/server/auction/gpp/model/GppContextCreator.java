package org.prebid.server.auction.gpp.model;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.error.DecodingException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class GppContextCreator {

    public static GppContextBuilder from(String gpp, List<Integer> gppSid) {
        final List<String> errors = new ArrayList<>();

        GppModel gppModel;
        try {
            gppModel = gppModel(gpp);
        } catch (PreBidException e) {
            gppModel = null;
            errors.add(e.getMessage());
        }

        final Set<Integer> sectionIds = gppSid != null
                ? Set.copyOf(gppSid)
                : null;

        return GppContextBuilder.of(
                GppContext.Scope.of(gppModel, sectionIds),
                GppContext.Regions.builder(),
                errors);
    }

    private static GppModel gppModel(String gpp) {
        if (StringUtils.isEmpty(gpp)) {
            return null;
        }

        try {
            return new GppModel(gpp);
        } catch (DecodingException e) {
            throw new PreBidException("GPP string invalid: " + e.getMessage());
        }
    }

    @Value
    @AllArgsConstructor(access = AccessLevel.PRIVATE, staticName = "of")
    public static class GppContextBuilder {

        GppContext.Scope scope;

        GppContext.Regions.RegionsBuilder regionsBuilder;

        List<String> errors;

        public GppContextBuilder withTcfEuV2(Integer gdpr, String consent) {
            regionsBuilder.tcfEuV2Privacy(GppContext.Regions.TcfEuV2Privacy.of(gdpr, consent));
            return this;
        }

        public GppContextBuilder withUspV1(String usPrivacy) {
            regionsBuilder.uspV1Privacy(GppContext.Regions.UspV1Privacy.of(usPrivacy));
            return this;
        }

        public GppContext build() {
            return GppContext.of(scope, regionsBuilder.build(), errors);
        }
    }
}
