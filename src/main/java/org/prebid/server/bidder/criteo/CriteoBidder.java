package org.prebid.server.bidder.criteo;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.criteo.model.CriteoGdprConsent;
import org.prebid.server.bidder.criteo.model.CriteoPublisher;
import org.prebid.server.bidder.criteo.model.CriteoRequest;
import org.prebid.server.bidder.criteo.model.CriteoRequestSlot;
import org.prebid.server.bidder.criteo.model.CriteoResponse;
import org.prebid.server.bidder.criteo.model.CriteoResponseSlot;
import org.prebid.server.bidder.criteo.model.CriteoUser;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtImpCriteo;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Criteo {@link Bidder} implementation.
 */
public class CriteoBidder implements Bidder<CriteoRequest> {

    private final String endpointUrl;
    private final JacksonMapper jsonMapper;
    private final boolean generateSlotId;

    public CriteoBidder(String endpointUrl, JacksonMapper jsonMapper, boolean generateSlotId) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.jsonMapper = Objects.requireNonNull(jsonMapper);
        this.generateSlotId = generateSlotId;
    }

    @Override
    public Result<List<HttpRequest<CriteoRequest>>> makeHttpRequests(BidRequest bidRequest) {

        final List<Imp> imps = bidRequest.getImp();
        final List<CriteoRequestSlot> requestSlots = new ArrayList<>();
        Integer networkId = null;
        for (Imp imp : imps) {
            final ExtImpCriteo extImpCriteo;
            try {
                extImpCriteo = parseImpExt(imp);
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }

            final Integer impExtNetworkId = extImpCriteo.getNetworkId();
            if (networkId == null && isPositiveInteger(impExtNetworkId)) {
                networkId = impExtNetworkId;
            } else if (networkId != null && !networkId.equals(impExtNetworkId)) {
                return Result.withError(BidderError.badInput("Bid request has slots coming with several network "
                        + "IDs which is not allowed"));
            }
            requestSlots.add(resolveSlot(imp, extImpCriteo));
        }

        final CriteoRequest outgoingRequest = createRequest(bidRequest, requestSlots, networkId);

        return Result.withValue(HttpRequest.<CriteoRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .body(jsonMapper.encode(outgoingRequest))
                .headers(resolveHeaders(outgoingRequest))
                .payload(outgoingRequest)
                .build());
    }

    private ExtImpCriteo parseImpExt(Imp imp) {
        try {
            return jsonMapper.mapper().convertValue(imp.getExt().get("bidder"), ExtImpCriteo.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private CriteoRequestSlot resolveSlot(Imp imp, ExtImpCriteo extImpCriteo) {
        return CriteoRequestSlot.builder()
                .impId(imp.getId())
                .slotId(generateSlotId ? UUID.randomUUID().toString() : null)
                .sizes(resolveSlotSizes(imp.getBanner()))
                .zoneId(getIfValid(extImpCriteo.getZoneId()))
                .networkId(getIfValid(extImpCriteo.getNetworkId()))
                .build();
    }

    private static List<String> resolveSlotSizes(Banner banner) {
        if (banner == null) {
            return null;
        }
        final List<String> sizes = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(banner.getFormat())) {
            for (Format format : banner.getFormat()) {
                sizes.add(formatSizesAsString(format.getW(), format.getH()));
            }
        } else if (isPositiveInteger(banner.getW()) && isPositiveInteger(banner.getH())) {
            sizes.add(formatSizesAsString(banner.getW(), banner.getH()));
        }
        return sizes;
    }

    private static CriteoRequest createRequest(BidRequest bidRequest,
                                               List<CriteoRequestSlot> requestSlots,
                                               Integer networkId) {
        final CriteoRequest.CriteoRequestBuilder criteoRequestBuilder = CriteoRequest.builder()
                .id(bidRequest.getId())
                .slots(requestSlots);

        final Regs regs = bidRequest.getRegs();
        final ExtRegs extRegs = regs != null ? regs.getExt() : null;

        final User user = bidRequest.getUser();
        final ExtUser extUser = user != null ? user.getExt() : null;
        final Device device = bidRequest.getDevice();

        criteoRequestBuilder
                .publisher(resolvePublisher(bidRequest, networkId))
                .user(resolveUser(user, device, extRegs))
                .gdprConsent(resolveGdprConsent(extUser, extRegs));

        if (extUser != null) {
            criteoRequestBuilder.eids(extUser.getEids());
        }

        return criteoRequestBuilder.build();
    }

    private static String formatSizesAsString(Integer w, Integer h) {
        return String.format("%sx%s", w, h);
    }

    private static Integer getIfValid(Integer number) {
        return isPositiveInteger(number) ? number : null;
    }

    private static boolean isPositiveInteger(Integer integer) {
        return integer != null && integer > 0;
    }

    private static CriteoPublisher resolvePublisher(BidRequest bidRequest, Integer networkId) {
        final App app = bidRequest.getApp();
        final String appBundle = app != null ? app.getBundle() : null;
        final Site site = bidRequest.getSite();
        final String siteId = site != null ? site.getId() : null;
        final String page = site != null ? site.getPage() : null;
        return CriteoPublisher.builder()
                .networkId(networkId)
                .bundleId(appBundle)
                .siteId(siteId)
                .url(page)
                .build();
    }

    private static CriteoUser resolveUser(User user, Device device, ExtRegs extRegs) {
        if (user == null && device == null) {
            return null;
        }

        final String userCookieId = user != null ? user.getBuyeruid() : null;
        final CriteoUser.CriteoUserBuilder userBuilder = CriteoUser.builder().cookieId(userCookieId);

        if (device != null) {
            userBuilder.deviceIdType(resolveDeviceType(device.getOs()))
                    .deviceOs(device.getOs())
                    .deviceId(device.getIfa())
                    .ip(device.getIp())
                    .ipV6(device.getIpv6())
                    .userAgent(device.getUa());
        }

        if (extRegs != null) {
            userBuilder.uspIab(extRegs.getUsPrivacy());
        }

        return userBuilder.build();
    }

    private static String resolveDeviceType(String deviceOs) {
        final String lowerCaseDeviceOs = StringUtils.stripToEmpty(deviceOs).toLowerCase();
        switch (lowerCaseDeviceOs) {
            case "ios":
                return "idfa";
            case "android":
                return "gaid";
            default:
                return "unknown";
        }
    }

    private static CriteoGdprConsent resolveGdprConsent(final ExtUser extUser, ExtRegs extRegs) {
        final String consentData = extUser != null ? extUser.getConsent() : null;
        final Integer gdpr = extRegs != null ? extRegs.getGdpr() : null;
        return CriteoGdprConsent.builder()
                .consentData(consentData)
                .gdprApplies(Objects.equals(gdpr, 1))
                .build();
    }

    private static MultiMap resolveHeaders(CriteoRequest criteoRequest) {
        final MultiMap headers = HttpUtil.headers();
        final CriteoUser criteoUser = criteoRequest.getUser();

        if (criteoUser == null) {
            return headers;
        }
        final String cookieId = criteoUser.getCookieId();
        if (StringUtils.isNotEmpty(cookieId)) {
            headers.add(HttpUtil.COOKIE_HEADER, String.format("uid=%s", cookieId));
        }
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, criteoUser.getIp());
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, criteoUser.getIpV6());
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, criteoUser.getUserAgent());
        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<CriteoRequest> httpCall, BidRequest bidRequest) {
        try {
            final CriteoResponse criteoResponse
                    = jsonMapper.decodeValue(httpCall.getResponse().getBody(), CriteoResponse.class);
            return Result.withValues(extractBidsFromResponse(criteoResponse));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBidsFromResponse(CriteoResponse criteoResponse) {
        return criteoResponse.getSlots().stream()
                .filter(Objects::nonNull)
                .map(CriteoBidder::slotToBidderBid)
                .collect(Collectors.toList());
    }

    private static BidderBid slotToBidderBid(CriteoResponseSlot slot) {
        return BidderBid.of(slotToBid(slot), BidType.banner, slot.getCurrency());
    }

    private static Bid slotToBid(CriteoResponseSlot slot) {

        return Bid.builder()
                .id(slot.getId())
                .impid(slot.getImpId())
                .price(slot.getCpm())
                .adm(slot.getCreative())
                .w(slot.getWidth())
                .h(slot.getHeight())
                .crid(slot.getCreativeId())
                .build();
    }
}
