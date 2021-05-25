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
import org.prebid.server.identity.IdGenerator;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtImpCriteo;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class CriteoBidder implements Bidder<CriteoRequest> {

    private static final String TEST_UUID = "e77e016a-091d-4cdb-a759-29d57a1ffa48";

    private final String endpointUrl;
    private final JacksonMapper jsonMapper;
    private final IdGenerator idGenerator;

    public CriteoBidder(String endpointUrl, JacksonMapper jsonMapper, IdGenerator idGenerator) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.jsonMapper = Objects.requireNonNull(jsonMapper);
        this.idGenerator = Objects.requireNonNull(idGenerator);
    }

    @Override
    public Result<List<HttpRequest<CriteoRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final CriteoRequest.CriteoRequestBuilder criteoRequestBuilder = CriteoRequest.builder()
                .id(bidRequest.getId());

        final boolean isTestMode = determineIfTestMode(bidRequest);

        Integer networkId = null;

        final List<Imp> imps = bidRequest.getImp();
        final List<CriteoRequestSlot> criteoRequestSlots = new ArrayList<>();
        for (Imp imp : imps) {
            ExtImpCriteo extImpCriteo;
            try {
                extImpCriteo = parseImpExt(imp);
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
            criteoRequestSlots.add(buildRequestSlot(imp, extImpCriteo, isTestMode));
            networkId = extImpCriteo.getNetworkId();
        }

        if (allSlotsAreContainingSameNetworkId(criteoRequestSlots)) {
            return Result.withError(
                    BidderError.badInput("Bid request has slots coming with several network IDs which is not allowed")
            );
        }

        criteoRequestBuilder.slots(criteoRequestSlots);

        final ExtRegs extRegs = getExtRegs(bidRequest);
        criteoRequestBuilder
                .publisher(buildCriteoPublisher(bidRequest, networkId))
                .user(buildCriteoUser(bidRequest, extRegs))
                .gdprConsent(buildCriteoGdprConsent(bidRequest, extRegs));

        final ExtUser extUser = bidRequest.getUser() != null ? bidRequest.getUser().getExt() : null;
        if (extUser != null) {
            criteoRequestBuilder.eids(extUser.getEids());
        }

        final CriteoRequest criteoRequest = criteoRequestBuilder.build();
        final String requestBody;
        try {
            requestBody = jsonMapper.encode(criteoRequest);
        } catch (EncodeException e) {
            return Result.withError(BidderError.badInput(
                    String.format("Failed to encode request body, error: %s", e.getMessage())));
        }

        return Result.withValue(HttpRequest.<CriteoRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .body(requestBody)
                .headers(resolveHeaders(criteoRequest))
                .payload(criteoRequest)
                .build());
    }

    private static boolean determineIfTestMode(BidRequest bidRequest) {
        return bidRequest.getId() != null && bidRequest.getId().startsWith("pbj-test");
    }

    private CriteoRequestSlot buildRequestSlot(Imp imp, ExtImpCriteo extImpCriteo, boolean isTestMode) {
        return CriteoRequestSlot.builder()
                .impId(imp.getId())
                .slotId(generateUuid(isTestMode))
                .sizes(getImpSizesFromBanner(imp.getBanner()))
                .zoneId(toPositiveNumberOrNull(extImpCriteo.getZoneId()))
                .networkId(toPositiveNumberOrNull(extImpCriteo.getNetworkId()))
                .build();
    }

    private String generateUuid(boolean isTestMode) {
        return isTestMode ? TEST_UUID : idGenerator.generateId();
    }

    private static List<String> getImpSizesFromBanner(Banner banner) {
        if (banner == null) {
            return new ArrayList<>();
        }

        final List<String> sizes = new ArrayList<>();
        if (banner.getFormat() != null) {
            for (Format format : banner.getFormat()) {
                sizes.add(formatSizesAsString(format.getW(), format.getH()));
            }
        } else if (banner.getW() != null && banner.getW() > 0
                && banner.getH() != null && banner.getH() > 0) {
            sizes.add(formatSizesAsString(banner.getW(), banner.getH()));
        }
        return sizes;
    }

    private static String formatSizesAsString(Integer w, Integer h) {
        return String.format("%sx%s", w, h);
    }

    private static Integer toPositiveNumberOrNull(Integer number) {
        return number != null && number > 0 ? number : null;
    }

    private ExtImpCriteo parseImpExt(Imp imp) {
        try {
            return jsonMapper.mapper().convertValue(imp.getExt().get("bidder"), ExtImpCriteo.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static boolean allSlotsAreContainingSameNetworkId(List<CriteoRequestSlot> criteoRequestSlots) {
        return criteoRequestSlots.stream()
                .map(CriteoRequestSlot::getNetworkId)
                .filter(Objects::nonNull)
                .distinct()
                .count() > 1;
    }

    private static ExtRegs getExtRegs(BidRequest bidRequest) {
        final Regs regs = bidRequest.getRegs();
        return regs != null ? regs.getExt() : null;
    }

    private static CriteoPublisher buildCriteoPublisher(BidRequest bidRequest, Integer networkId) {
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

    private static CriteoUser buildCriteoUser(BidRequest bidRequest, ExtRegs extRegs) {
        final User user = bidRequest.getUser();
        final String userCookieId = user != null ? user.getBuyeruid() : null;

        final CriteoUser.CriteoUserBuilder builder = CriteoUser.builder().cookieId(userCookieId);

        final Device device = bidRequest.getDevice();
        if (device != null) {
            builder.deviceIdType(determineDeviceIdType(bidRequest.getDevice().getOs()))
                    .deviceOs(bidRequest.getDevice().getOs())
                    .deviceId(bidRequest.getDevice().getIfa())
                    .ip(bidRequest.getDevice().getIp())
                    .ipV6(bidRequest.getDevice().getIpv6())
                    .userAgent(bidRequest.getDevice().getUa());
        }

        if (extRegs != null) {
            builder.uspIab(extRegs.getUsPrivacy());
        }

        return builder.build();
    }

    private static String determineDeviceIdType(String deviceOs) {
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

    private static CriteoGdprConsent buildCriteoGdprConsent(BidRequest bidRequest, ExtRegs extRegs) {
        final User user = bidRequest.getUser();
        final ExtUser extUser = user != null ? user.getExt() : null;
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
        HttpUtil.addHeaderIfValueIsNotEmpty(
                headers, HttpUtil.COOKIE_HEADER, String.format("uid=%s", criteoUser.getCookieId()));
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
        Double cpm = slot.getCpm();
        BigDecimal price = cpm != null ? BigDecimal.valueOf(cpm) : null;
        return Bid.builder()
                .id(slot.getId())
                .impid(slot.getImpId())
                .price(price)
                .adm(slot.getCreative())
                .w(slot.getWidth())
                .h(slot.getHeight())
                .crid(slot.getCreativeId())
                .build();
    }

}
