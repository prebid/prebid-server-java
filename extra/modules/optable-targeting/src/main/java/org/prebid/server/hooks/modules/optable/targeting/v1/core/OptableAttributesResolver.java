package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OptableAttributesResolver {

    private OptableAttributesResolver() {
    }

    public static OptableAttributes resolveAttributes(AuctionContext auctionContext, Long timeout) {
        final GppContext.Scope gppScope = auctionContext.getGppContext().scope();

        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Optional<Regs> regs = Optional.ofNullable(bidRequest.getRegs());
        final Integer gdpr = regs
                .map(Regs::getGdpr)
                .orElseGet(() -> regs.map(Regs::getExt)
                        .map(ExtRegs::getGdpr)
                        .orElse(null));

        final OptableAttributes.OptableAttributesBuilder builder = OptableAttributes.builder()
                .ips(resolveIp(auctionContext))
                .userAgent(resolveUserAgent(auctionContext))
                .timeout(timeout);

        if (gdpr != null && gdpr > 0) {
            final Optional<User> user = Optional.ofNullable(bidRequest.getUser());
            final String consent = user.map(User::getConsent)
                    .orElseGet(() -> user.map(User::getExt)
                    .map(ExtUser::getConsent)
                    .orElse(null));

            if (StringUtils.isNotEmpty(consent)) {
                builder
                        .gdprApplies(true)
                        .gdprConsent(consent);
            }
        }

        if (gppScope.getGppModel() != null) {
            builder
                    .gpp(gppScope.getGppModel().encode())
                    .gppSid(SetUtils.emptyIfNull(gppScope.getSectionsIds()));
        }

        return builder.build();
    }

    public static String resolveUserAgent(AuctionContext auctionContext) {
        final Device device = auctionContext.getBidRequest().getDevice();
        return device != null ? device.getUa() : null;
    }

    private static List<String> resolveIp(AuctionContext auctionContext) {
        final List<String> result = new ArrayList<>();

        final Optional<Device> deviceOpt = Optional.ofNullable(auctionContext.getBidRequest().getDevice());
        deviceOpt.map(Device::getIp).ifPresent(result::add);
        deviceOpt.map(Device::getIpv6).ifPresent(result::add);

        if (result.isEmpty()) {
            Optional.ofNullable(auctionContext.getPrivacyContext().getIpAddress())
                    .ifPresent(result::add);
        }

        return result;
    }
}
