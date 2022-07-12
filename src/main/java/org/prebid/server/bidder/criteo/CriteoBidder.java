package org.prebid.server.bidder.criteo;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.apache.commons.lang3.ObjectUtils;
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
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.identity.IdGenerator;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtImpCriteo;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class CriteoBidder implements Bidder<CriteoRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpCriteo>> CRITEO_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final IdGenerator idGenerator;
    private final JacksonMapper mapper;

    public CriteoBidder(String endpointUrl,
                        IdGenerator idGenerator,
                        JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<CriteoRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<CriteoRequestSlot> requestSlots = new ArrayList<>();
        Integer networkId = null;

        try {
            for (Imp imp : bidRequest.getImp()) {
                final ExtImpCriteo extImpCriteo = parseImpExt(imp);
                final Integer impExtNetworkId = extImpCriteo.getNetworkId();

                networkId = ObjectUtils.firstNonNull(
                        networkId,
                        stripNonPositiveToNull(impExtNetworkId));

                validateNetworkIds(networkId, impExtNetworkId);

                requestSlots.add(resolveSlot(imp, extImpCriteo));
            }
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        return Result.withValue(createHttpRequest(createCriterioRequest(bidRequest, requestSlots, networkId)));
    }

    private ExtImpCriteo parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), CRITEO_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private static Integer stripNonPositiveToNull(Integer value) {
        return isPositive(value) ? value : null;
    }

    private static boolean isPositive(Integer value) {
        return value != null && value > 0;
    }

    private static void validateNetworkIds(Integer globalNetworkId, Integer currentNetworkId) {
        if (globalNetworkId != null && !globalNetworkId.equals(currentNetworkId)) {
            throw new PreBidException("Bid request has slots coming with several network IDs which is not allowed");
        }
    }

    private CriteoRequestSlot resolveSlot(Imp imp, ExtImpCriteo extImpCriteo) {
        return CriteoRequestSlot.builder()
                .impId(imp.getId())
                .slotId(idGenerator.generateId())
                .sizes(resolveSlotSizes(imp.getBanner()))
                .zoneId(stripNonPositiveToNull(extImpCriteo.getZoneId()))
                .networkId(stripNonPositiveToNull(extImpCriteo.getNetworkId()))
                .build();
    }

    private static List<String> resolveSlotSizes(Banner banner) {
        if (banner == null) {
            return null;
        }

        final List<Format> formats = banner.getFormat();
        if (CollectionUtils.isNotEmpty(formats)) {
            return formats.stream()
                    .map(format -> formatSizesAsString(format.getW(), format.getH()))
                    .toList();
        }

        final Integer width = banner.getW();
        final Integer height = banner.getH();
        if (isPositive(width) && isPositive(height)) {
            return Collections.singletonList(formatSizesAsString(width, height));
        }

        return Collections.emptyList();
    }

    private static String formatSizesAsString(Integer w, Integer h) {
        return "%sx%s".formatted(w, h);
    }

    private static CriteoRequest createCriterioRequest(BidRequest bidRequest,
                                                       List<CriteoRequestSlot> requestSlots,
                                                       Integer networkId) {

        final User user = bidRequest.getUser();
        final ExtUser extUser = user != null ? user.getExt() : null;
        final Device device = bidRequest.getDevice();
        final ExtRegs extRegs = ObjectUtil.getIfNotNull(bidRequest.getRegs(), Regs::getExt);

        return CriteoRequest.builder()
                .id(bidRequest.getId())
                .slots(requestSlots)
                .publisher(resolvePublisher(bidRequest, networkId))
                .user(resolveUser(user, device, extRegs))
                .gdprConsent(resolveGdprConsent(extUser, extRegs))
                .eids(extUser != null ? extUser.getEids() : null)
                .build();
    }

    private static CriteoPublisher resolvePublisher(BidRequest bidRequest, Integer networkId) {
        final Site site = bidRequest.getSite();

        return CriteoPublisher.builder()
                .networkId(networkId)
                .bundleId(ObjectUtil.getIfNotNull(bidRequest.getApp(), App::getBundle))
                .siteId(site != null ? site.getId() : null)
                .url(site != null ? site.getPage() : null)
                .build();
    }

    private static CriteoUser resolveUser(User user, Device device, ExtRegs extRegs) {
        if (user == null && device == null) {
            return null;
        }

        return CriteoUser.builder()
                .cookieId(user != null ? user.getBuyeruid() : null)
                .deviceIdType(device != null ? resolveDeviceType(device.getOs()) : null)
                .deviceOs(device != null ? device.getOs() : null)
                .deviceId(device != null ? device.getIfa() : null)
                .ip(device != null ? device.getIp() : null)
                .ipV6(device != null ? device.getIpv6() : null)
                .userAgent(device != null ? device.getUa() : null)
                .uspIab(extRegs != null ? extRegs.getUsPrivacy() : null)
                .build();
    }

    private static String resolveDeviceType(String deviceOs) {
        final String lowerCaseDeviceOs = StringUtils.stripToEmpty(deviceOs).toLowerCase();
        return switch (lowerCaseDeviceOs) {
            case "ios" -> "idfa";
            case "android" -> "gaid";
            default -> "unknown";
        };
    }

    private static CriteoGdprConsent resolveGdprConsent(ExtUser extUser, ExtRegs extRegs) {
        return CriteoGdprConsent.builder()
                .consentData(extUser != null ? extUser.getConsent() : null)
                .gdprApplies(Objects.equals(extRegs != null ? extRegs.getGdpr() : null, 1))
                .build();
    }

    private HttpRequest<CriteoRequest> createHttpRequest(CriteoRequest request) {
        return HttpRequest.<CriteoRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(resolveHeaders(request))
                .body(mapper.encodeToBytes(request))
                .payload(request)
                .build();
    }

    private static MultiMap resolveHeaders(CriteoRequest criteoRequest) {
        final MultiMap headers = HttpUtil.headers();
        final CriteoUser criteoUser = criteoRequest.getUser();
        if (criteoUser == null) {
            return headers;
        }

        final String cookieId = criteoUser.getCookieId();
        HttpUtil.addHeaderIfValueIsNotEmpty(
                headers,
                HttpUtil.COOKIE_HEADER,
                StringUtils.isNotEmpty(cookieId) ? "uid=" + cookieId : null);
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, criteoUser.getIp());
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, criteoUser.getIpV6());
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, criteoUser.getUserAgent());

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<CriteoRequest> httpCall, BidRequest bidRequest) {
        try {
            final CriteoResponse criteoResponse = mapper.decodeValue(
                    httpCall.getResponse().getBody(), CriteoResponse.class);

            return Result.withValues(extractBidsFromResponse(criteoResponse));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBidsFromResponse(CriteoResponse criteoResponse) {
        if (criteoResponse == null || CollectionUtils.isEmpty(criteoResponse.getSlots())) {
            return Collections.emptyList();
        }

        return criteoResponse.getSlots().stream()
                .filter(Objects::nonNull)
                .map(CriteoBidder::slotToBidderBid)
                .toList();
    }

    private static BidderBid slotToBidderBid(CriteoResponseSlot slot) {
        return BidderBid.of(slotToBid(slot), BidType.banner, slot.getCurrency());
    }

    private static Bid slotToBid(CriteoResponseSlot slot) {
        return Bid.builder()
                .id(slot.getArbitrageId())
                .impid(slot.getImpId())
                .price(slot.getCpm())
                .adm(slot.getCreative())
                .w(slot.getWidth())
                .h(slot.getHeight())
                .crid(slot.getCreativeCode())
                .build();
    }
}
