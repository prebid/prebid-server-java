package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.gpp.encoder.GppModel;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;
import org.prebid.server.privacy.model.PrivacyContext;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@AllArgsConstructor
public class OptableAttributesResolver {

    private final IpResolver ipResolver;

    private static final int SECTION_ID_CANADA = 5;

    private static final Set<Integer> SECTION_ID_EUROPE = Set.of(1, 2);

    private static final Set<Integer> SECTION_ID_US =
            Set.of(6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22);

    private static final Set<String> GDPR_COUNTRIES = Set.of("Belgium", "Bulgaria", "Cyprus", "Denmark",
            "Germany", "Estonia", "Finland", "France", "Greece", "Hungary", "Ireland", "Italy", "Croatia", "Latvia",
            "Liechtenstein", "Lithuania", "Luxembourg", "Malta", "The Netherlands", "Norway",
            "Austria", "Poland", "Portugal", "Romania", "Slovakia", "Slovenia", "Spain", "Switzerland", "UK");

    private static final Set<String> GDPR_REGIONS = Set.of("Azores", "Canary", "Islands", "Guadeloupe", "French Guiana",
            "Madeira", "Martinique", "Mayotte", "Reunion", "Saint Martin");

    public OptableAttributes reloveAttributes(AuctionContext auctionContext, Long timeout) {
        final List<String> ips = ipResolver.resolveIp(auctionContext);

        OptableAttributes optableAttributes = getTcfPrivacyAttributes(auctionContext);
        if (optableAttributes == null) {
            optableAttributes = getGppPrivacyAttributes(auctionContext);
        }
        if (optableAttributes == null) {
            optableAttributes = getGeoIpPrivacyAttributes(auctionContext);
        }

        return optableAttributes != null
                ? optableAttributes.toBuilder().ips(ips).timeout(timeout).build()
                : OptableAttributes.builder().ips(ips).timeout(timeout).build();
    }

    private OptableAttributes getGeoIpPrivacyAttributes(AuctionContext auctionContext) {
        final Optional<GeoInfo> geoInfoOpt = Optional.ofNullable(auctionContext).map(AuctionContext::getGeoInfo);

        final String country = geoInfoOpt.map(GeoInfo::getCountry).orElse(null);
        final String region = geoInfoOpt.map(GeoInfo::getRegion).orElse(null);

        if (StringUtils.isNotEmpty(country)) {
            if (country.equalsIgnoreCase("US") || country.equalsIgnoreCase("United States")) {
                return OptableAttributes.of("us");
            } else if (country.equalsIgnoreCase("Quebec") || country.equalsIgnoreCase("Canada")) {
                return OptableAttributes.of("can");
            } else if (GDPR_COUNTRIES.contains(country)) {
                return OptableAttributes.of("gdpr");
            }
        }

        if (StringUtils.isNotEmpty(region) && GDPR_REGIONS.contains(region)) {
            return OptableAttributes.of("gdpr");
        }

        return null;
    }

    private OptableAttributes getGppPrivacyAttributes(AuctionContext auctionContext) {
        final Optional<GppContext> gppContextOpt = Optional.ofNullable(auctionContext)
                .map(AuctionContext::getGppContext);

        final Optional<GppContext.Scope> gppScope = gppContextOpt
                .map(GppContext::scope);

        final String gppConsent = gppScope.map(GppContext.Scope::getGppModel)
                .map(GppModel::encode)
                .orElse(null);

        if (gppConsent != null) {
            final Set<Integer> sids = gppContextOpt
                    .map(GppContext::scope)
                    .map(GppContext.Scope::getSectionsIds)
                    .orElse(Set.of());

            return OptableAttributes.of(sidsToReg(sids)).toBuilder().gpp(gppConsent).gppSid(sids).build();

        }

        return null;
    }

    private OptableAttributes getTcfPrivacyAttributes(AuctionContext auctionContext) {
        return Optional.ofNullable(auctionContext)
                        .map(AuctionContext::getPrivacyContext)
                .map(PrivacyContext::getTcfContext)
                .map(ctx -> {
                    if (ctx.isConsentValid()) {
                        return OptableAttributes.of("gdpr").toBuilder().tcf(ctx.getConsentString()).build();
                    }
                    return null;
                }).orElse(null);
    }

    private String sidsToReg(Set<Integer> sids) {
        if (sids == null) {
            return null;
        }

        if (sids.contains(SECTION_ID_CANADA)) {
            return "can";
        } else if (sids.stream().anyMatch(SECTION_ID_EUROPE::contains)) {
            return "gdpr";
        } else if (sids.stream().anyMatch(SECTION_ID_US::contains)) {
            return "us";
        }

        return null;
    }
}
