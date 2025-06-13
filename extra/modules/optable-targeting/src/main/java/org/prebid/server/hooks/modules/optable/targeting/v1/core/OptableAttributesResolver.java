package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.PrivacyContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OptableAttributesResolver {

    public static OptableAttributes resolveAttributes(AuctionContext auctionContext, Long timeout) {
        final TcfContext tcfContext = auctionContext.getPrivacyContext().getTcfContext();
        final GppContext.Scope gppScope = auctionContext.getGppContext().scope();

        final OptableAttributes.OptableAttributesBuilder builder = OptableAttributes.builder()
                .ips(resolveIp(auctionContext))
                .timeout(timeout);

        if (tcfContext.isConsentValid()) {
            builder
                    .gdprApplies(tcfContext.isInGdprScope())
                    .gdprConsent(tcfContext.getConsentString());
        }

        if (gppScope.getGppModel() != null) {
            builder
                    .gpp(gppScope.getGppModel().encode())
                    .gppSid(SetUtils.emptyIfNull(gppScope.getSectionsIds()));
        }

        return builder.build();
    }

    public static List<String> resolveIp(AuctionContext auctionContext) {
        final List<String> result = new ArrayList<>();

        final Optional<AuctionContext> auctionContextOpt = Optional.ofNullable(auctionContext);

        final Optional<Device> deviceOpt = auctionContextOpt
                .map(AuctionContext::getBidRequest)
                .map(BidRequest::getDevice);

        deviceOpt.map(Device::getIp).ifPresent(result::add);
        deviceOpt.map(Device::getIpv6).ifPresent(result::add);

        if (result.isEmpty()) {
            auctionContextOpt.map(AuctionContext::getPrivacyContext)
                    .map(PrivacyContext::getIpAddress)
                    .ifPresent(result::add);
        }

        return result;
    }

    public static String resolveIp(List<String> ips) {
        return CollectionUtils.isNotEmpty(ips) ? ips.getFirst() : "none";
    }
}
