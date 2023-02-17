package org.prebid.server.auction.gpp.model;

import com.iab.gpp.encoder.GppModel;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.auction.gpp.model.privacy.Privacy;
import org.prebid.server.exception.PreBidException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class GppContextCreator {

    private GppContextCreator() {
    }

    public static GppContextBuilder from(String gpp, List<Integer> gppSid) {
        final List<String> errors = new ArrayList<>();

        GppModel gppModel;
        try {
            gppModel = GppContextUtils.gppModel(gpp);
        } catch (PreBidException e) {
            gppModel = null;
            errors.add(e.getMessage());
        }

        final Set<Integer> sectionIds = gppSid != null
                ? Set.copyOf(gppSid)
                : null;

        return GppContextBuilder.of(GppContext.Scope.of(gppModel, sectionIds), errors);
    }

    @Value
    @AllArgsConstructor(access = AccessLevel.PRIVATE, staticName = "of")
    public static class GppContextBuilder {

        GppContext.Scope scope;

        GppContext.Regions.RegionsBuilder regionsBuilder = GppContextUtils.DEFAULT_REGIONS_BUILDER;

        List<String> errors;

        public GppContextBuilder with(Privacy privacy) {
            GppContextUtils.withPrivacy(regionsBuilder, privacy);
            return this;
        }

        public GppContext build() {
            return new GppContext(scope, regionsBuilder.build(), errors);
        }
    }
}
