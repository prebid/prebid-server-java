package org.prebid.server.bidder.yahooads;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
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
import org.prebid.server.auction.versionconverter.BidRequestOrtbVersionConversionManager;
import org.prebid.server.auction.versionconverter.OrtbVersion;
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
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsa;
import org.prebid.server.proto.openrtb.ext.request.yahooads.ExtImpYahooAds;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class YahooAdsBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpYahooAds>> YAHOO_ADVERTISING_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final BidRequestOrtbVersionConversionManager conversionManager;
    private final JacksonMapper mapper;

    public YahooAdsBidder(String endpointUrl,
                          BidRequestOrtbVersionConversionManager conversionManager,
                          JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
        this.conversionManager = Objects.requireNonNull(conversionManager);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<HttpRequest<BidRequest>> bidRequests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        final Regs regs = bidRequest.getRegs();
        final BidRequest bidRequestOpenRtb25 = this.conversionManager.convertFromAuctionSupportedVersion(bidRequest,
                OrtbVersion.ORTB_2_5);

        final List<Imp> impList = bidRequestOpenRtb25.getImp();
        for (int i = 0; i < impList.size(); i++) {
            try {
                final Imp imp = impList.get(i);
                final ExtImpYahooAds extImpYahooAds = parseAndValidateImpExt(imp.getExt(), i);
                final BidRequest modifiedRequest = modifyRequest(bidRequestOpenRtb25, imp, extImpYahooAds,
                        regs);
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

    private BidRequest modifyRequest(BidRequest request, Imp imp, ExtImpYahooAds extImpYahooAds,
                                     Regs regs) {
        final BidRequest.BidRequestBuilder requestBuilder = request.toBuilder();

        final Site site = request.getSite();
        final App app = request.getApp();

        if (site != null) {
            requestBuilder.site(site.toBuilder().id(extImpYahooAds.getDcn()).build());
        } else if (app != null) {
            requestBuilder.app(app.toBuilder().id(extImpYahooAds.getDcn()).build());
        }

        if (regs != null) {
            requestBuilder.regs(modifyRegs(regs));
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
        final ExtRegs extRegs = resolveExtRegs(regs);

        return Regs.builder().ext(extRegs).build();
    }

    private ExtRegs resolveExtRegs(Regs regs) {
        final Integer gdpr = resolveGdpr(regs);
        final String usPrivacy = resolveUsPrivacy(regs);
        final String gpp = regs.getGpp();
        final List<Integer> gppSid = regs.getGppSid();

        final String gpc = Optional.ofNullable(regs.getExt())
                .map(ExtRegs::getGpc)
                .orElse(null);
        final ExtRegsDsa dsa = Optional.ofNullable(regs.getExt())
                .map(ExtRegs::getDsa)
                .orElse(null);
        final ExtRegs extRegs = ExtRegs.of(gdpr, usPrivacy, gpc, dsa);
        extRegs.addProperty("gpp", TextNode.valueOf(gpp));
        if (!CollectionUtils.isEmpty(gppSid)) {
            final ArrayNode gppArrayNode = mapper.mapper().createArrayNode();
            gppSid.forEach(gppArrayNode::add);
            extRegs.addProperty("gpp_sid", gppArrayNode);
        }
        if (regs.getCoppa() != null) {
            extRegs.addProperty("coppa", IntNode.valueOf(regs.getCoppa()));
        }

        Optional.ofNullable(regs.getExt())
                .map(FlexibleExtension::getProperties)
                .ifPresent(extRegs::addProperties);

        return extRegs;
    }

    private static Integer resolveGdpr(Regs regs) {
        return regs.getGdpr() != null ? regs.getGdpr()
                : (regs.getExt() != null ? regs.getExt().getGdpr() : null);
    }

    private static String resolveUsPrivacy(Regs regs) {
        return regs.getUsPrivacy() != null ? regs.getUsPrivacy()
                : (regs.getExt() != null ? regs.getExt().getUsPrivacy() : null);
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
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");

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
