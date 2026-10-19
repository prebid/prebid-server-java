package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import com.networknt.org.apache.commons.validator.routines.InetAddressValidator;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.impl.ActivityInvocationPayloadImpl;
import org.prebid.server.activity.infrastructure.payload.impl.BidRequestActivityInvocationPayload;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.optable.targeting.model.App;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.ExtUserOptable;
import org.prebid.server.hooks.modules.optable.targeting.v1.OptableTargetingModule;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OptableAttributesResolver {

    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String X_FORWARDED_FOR_HEADER_DELIMITER = ",";

    private OptableAttributesResolver() {
    }

    public static OptableAttributes resolveAttributes(AuctionContext auctionContext,
                                                      BidRequest maskedBidRequest,
                                                      Long timeout,
                                                      double logSamplingRate,
                                                      IpAddressHelper ipAddressHelper) {

        final GppContext.Scope gppScope = Optional.ofNullable(auctionContext.getGppContext())
                .map(GppContext::scope)
                .orElse(null);

        final Optional<Regs> regs = Optional.ofNullable(maskedBidRequest.getRegs());
        final Integer gdpr = regs
                .map(Regs::getGdpr)
                .orElseGet(() -> regs.map(Regs::getExt)
                        .map(ExtRegs::getGdpr)
                        .orElse(null));

        final Optional<User> user = Optional.ofNullable(maskedBidRequest.getUser());

        final OptableAttributes.OptableAttributesBuilder builder = OptableAttributes.builder()
                .ips(resolveIp(auctionContext, maskedBidRequest.getDevice(), ipAddressHelper))
                .userAgent(resolveUserAgent(maskedBidRequest.getDevice()))
                .app(resolveApp(maskedBidRequest.getApp()))
                .timeout(timeout);

        if (gdpr != null && gdpr > 0) {
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

        if (gppScope != null && gppScope.getGppModel() != null) {
            builder
                    .gpp(gppScope.getGppModel().encode())
                    .gppSid(SetUtils.emptyIfNull(gppScope.getSectionsIds()));
        }

        user.map(User::getExt)
                .map(ext -> ext.getProperty("optable"))
                .map(it -> ExtUserOptableResolver.resolveExtUserOptable(it, logSamplingRate))
                .map(ExtUserOptable::getId5Signature)
                .filter(StringUtils::isNotBlank)
                .ifPresent(builder::id5Signature);

        return builder.build();
    }

    public static String resolveUserAgent(Device device) {
        return device != null ? device.getUa() : null;
    }

    private static App resolveApp(com.iab.openrtb.request.App app) {
        return app != null ? App.of(app.getBundle(), app.getVer()) : null;
    }

    private static List<String> resolveIp(AuctionContext auctionContext,
                                          Device device, IpAddressHelper ipAddressHelper) {

        final List<String> result = new ArrayList<>();

        final Optional<Device> deviceOpt = Optional.ofNullable(device);
        deviceOpt.map(Device::getIp).ifPresent(result::add);
        deviceOpt.map(Device::getIpv6).ifPresent(result::add);

        if (result.isEmpty()) {
            Optional.ofNullable(auctionContext.getHttpRequest())
                    .map(HttpRequestContext::getHeaders)
                    .map(it -> it.get(X_FORWARDED_FOR_HEADER))
                    .map(it -> it.split(X_FORWARDED_FOR_HEADER_DELIMITER))
                    .filter(it -> it.length > 0)
                    .map(it -> it[0])
                    .ifPresent(ip -> result.add(
                            maskIp(auctionContext.getBidRequest(),
                                    ip,
                                    ipAddressHelper,
                                    auctionContext.getActivityInfrastructure())));
        }

        return result;
    }

    private static String maskIp(BidRequest bidRequest, String ip,
                                 IpAddressHelper ipAddressHelper, ActivityInfrastructure activityInfrastructure) {

        final ActivityInvocationPayload activityInvocationPayload = BidRequestActivityInvocationPayload.of(
                ActivityInvocationPayloadImpl.of(ComponentType.GENERAL_MODULE, OptableTargetingModule.CODE),
                bidRequest);

        final boolean disallowTransmitGeo =
                !activityInfrastructure.isAllowed(Activity.TRANSMIT_GEO, activityInvocationPayload);
        if (disallowTransmitGeo) {
            return maskIp(ip, ipAddressHelper);
        }

        return ip;
    }

    private static String maskIp(String ip, IpAddressHelper ipAddressHelper) {
        final boolean isIpv4 = InetAddressValidator.getInstance().isValidInet4Address(ip);
        if (isIpv4) {
            return ipAddressHelper.maskIpv4(ip);
        }
        final boolean isIpv6 = InetAddressValidator.getInstance().isValidInet6Address(ip);
        if (isIpv6) {
            return ipAddressHelper.anonymizeIpv6(ip);
        }

        return null;
    }
}
