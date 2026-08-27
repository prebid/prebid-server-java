package org.prebid.server.bidder.yahooads;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.yahooads.ExtImpYahooAds;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class YahooAdsBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpYahooAds>> YAHOO_ADVERTISING_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String OPENRTB_VERSION = "2.6";
    private static final String GPP_PROPERTY = "gpp";
    private static final String GPP_SID_PROPERTY = "gpp_sid";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public YahooAdsBidder(String endpointUrl,
                          JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<HttpRequest<BidRequest>> bidRequests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        final Regs regs = bidRequest.getRegs();
        final Regs modifiedRegs = regs != null ? modifyRegs(regs) : null;

        final List<Imp> impList = bidRequest.getImp();
        for (int i = 0; i < impList.size(); i++) {
            try {
                final Imp imp = impList.get(i);
                final ExtImpYahooAds extImpYahooAds = parseAndValidateImpExt(imp.getExt(), i);
                final BidRequest modifiedRequest = modifyRequest(bidRequest, imp, extImpYahooAds, modifiedRegs);
                bidRequests.add(makeHttpRequest(modifiedRequest));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(bidRequests, errors);
    }

    private ExtImpYahooAds parseAndValidateImpExt(ObjectNode impExtNode, int index) {
        final ExtImpYahooAds extImpYahooAds;
        try {
            extImpYahooAds = mapper.mapper().convertValue(impExtNode,
                    YAHOO_ADVERTISING_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("imp #%s: %s".formatted(index, e.getMessage()));
        }

        final String dcn = extImpYahooAds.getDcn();
        if (StringUtils.isBlank(dcn)) {
            throw new PreBidException("imp #%s: missing param dcn".formatted(index));
        }

        final String pos = extImpYahooAds.getPos();
        if (StringUtils.isBlank(pos)) {
            throw new PreBidException("imp #%s: missing param pos".formatted(index));
        }

        return extImpYahooAds;
    }

    private BidRequest modifyRequest(BidRequest request, Imp imp, ExtImpYahooAds extImpYahooAds, Regs regs) {
        final BidRequest.BidRequestBuilder requestBuilder = request.toBuilder();

        final Site site = request.getSite();
        final App app = request.getApp();

        if (site != null) {
            requestBuilder.site(site.toBuilder().id(extImpYahooAds.getDcn()).build());
        } else if (app != null) {
            requestBuilder.app(app.toBuilder().id(extImpYahooAds.getDcn()).build());
        }

        if (regs != null) {
            requestBuilder.regs(regs);
        }

        return requestBuilder
                .imp(Collections.singletonList(modifyImp(imp, extImpYahooAds)))
                .build();
    }

    private static Imp modifyImp(Imp imp, ExtImpYahooAds extImpYahooAds) {
        final Banner banner = imp.getBanner();
        return imp.toBuilder()
                .tagid(extImpYahooAds.getPos())
                .banner(banner != null ? modifyBanner(imp.getBanner()) : null)
                .build();
    }

    private static void validateBanner(Banner banner) {
        final Integer bannerWidth = banner.getW();
        final Integer bannerHeight = banner.getH();
        final boolean hasBannerWidthAndHeight = bannerWidth != null && bannerHeight != null;

        if (hasBannerWidthAndHeight && (bannerWidth == 0 || bannerHeight == 0)) {
            throw new PreBidException("Invalid sizes provided for Banner %sx%s".formatted(bannerWidth, bannerHeight));
        }
    }

    private static Banner modifyBanner(Banner banner) {
        validateBanner(banner);

        if (banner.getH() != null && banner.getW() != null) {
            return banner;
        }

        final List<Format> bannerFormats = banner.getFormat();
        if (CollectionUtils.isEmpty(bannerFormats)) {
            throw new PreBidException("No sizes provided for Banner");
        }
        final Format firstFormat = bannerFormats.getFirst();

        return banner.toBuilder()
                .w(firstFormat.getW())
                .h(firstFormat.getH())
                .build();
    }

    private Regs modifyRegs(Regs regs) {
        final ExtRegs ext = regs.getExt();
        if (ext == null || !(ext.containsProperty(GPP_PROPERTY) || ext.containsProperty(GPP_SID_PROPERTY))) {
            return regs;
        }

        final String gpp = regs.getGpp();
        final String resolvedGpp = gpp == null ? gppToPromote(ext) : gpp;

        final List<Integer> gppSid = regs.getGppSid();
        final List<Integer> promotedGppSid = CollectionUtils.isEmpty(gppSid) ? gppSidToPromote(ext) : null;
        final List<Integer> resolvedGppSid = promotedGppSid == null ? gppSid : promotedGppSid;

        final ExtRegs modifiedExt = copy(ext);
        if (resolvedGpp != null) {
            modifiedExt.removeProperty(GPP_PROPERTY);
        }
        if (CollectionUtils.isNotEmpty(resolvedGppSid)) {
            modifiedExt.removeProperty(GPP_SID_PROPERTY);
        }

        return regs.toBuilder()
                .gpp(resolvedGpp)
                .gppSid(resolvedGppSid)
                .ext(isExtEmpty(modifiedExt) ? null : modifiedExt)
                .build();
    }

    private static String gppToPromote(ExtRegs ext) {
        final JsonNode node = ext.getProperty(GPP_PROPERTY);
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private static List<Integer> gppSidToPromote(ExtRegs ext) {
        final JsonNode node = ext.getProperty(GPP_SID_PROPERTY);
        if (node == null || !node.isArray() || node.isEmpty()) {
            return null;
        }

        final List<Integer> sids = new ArrayList<>(node.size());
        for (final JsonNode elem : node) {
            if (!elem.isIntegralNumber() || !elem.canConvertToInt()) {
                return null;
            }
            sids.add(elem.intValue());
        }

        return sids;
    }

    private ExtRegs copy(ExtRegs ext) {
        final ExtRegs result = ExtRegs.of(ext.getGdpr(), ext.getUsPrivacy(), ext.getGpc(), ext.getDsa());
        mapper.fillExtension(result, ext.getProperties());
        return result;
    }

    private static boolean isExtEmpty(ExtRegs ext) {
        return ext.getGdpr() == null
                && ext.getUsPrivacy() == null
                && ext.getGpc() == null
                && ext.getDsa() == null
                && ext.getProperties().isEmpty();
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest outgoingRequest) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .body(mapper.encodeToBytes(outgoingRequest))
                .headers(makeHeaders(outgoingRequest.getDevice()))
                .payload(outgoingRequest)
                .build();
    }

    private static MultiMap makeHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, OPENRTB_VERSION);

        final String deviceUa = device != null ? device.getUa() : null;
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, deviceUa);

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, httpCall.getRequest().getPayload()), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        final List<SeatBid> seatBids = bidResponse != null ? bidResponse.getSeatbid() : null;
        if (seatBids == null) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, bidRequest.getImp());
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<Imp> imps) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, imps, bidResponse.getCur()))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid makeBidderBid(Bid bid, List<Imp> imps, String currency) {
        final BidType bidType = getBidType(bid, imps);
        return bidType != null
                ? BidderBid.of(bid, bidType, currency)
                : null;
    }

    private static BidType getBidType(Bid bid, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(bid.getImpid())) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                }
                return null;
            }
        }
        throw new PreBidException("Unknown ad unit code '%s'".formatted(bid.getImpid()));
    }
}
