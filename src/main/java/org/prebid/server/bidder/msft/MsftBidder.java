package org.prebid.server.bidder.msft;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.prebid.server.auction.model.Endpoint;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.appnexus.SameValueValidator;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.msft.proto.MsftBidExt;
import org.prebid.server.bidder.msft.proto.MsftBidExtCreative;
import org.prebid.server.bidder.msft.proto.MsftBidExtVideo;
import org.prebid.server.bidder.msft.proto.MsftExtImpOutgoing;
import org.prebid.server.bidder.msft.proto.ExtRequestMsft;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtAppPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidServer;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.msft.ExtImpMsft;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class MsftBidder implements Bidder<BidRequest> {

    private static final int MAX_IMPS_PER_REQUEST = 10;
    private static final String OPENRTB_VERSION = "2.6";

    private static final TypeReference<ExtPrebid<?, ExtImpMsft>> MSFT_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final int hbSource;
    private final int hbSourceVideo;
    private final Map<Integer, String> iabCategories;
    private final JacksonMapper mapper;

    public MsftBidder(String endpointUrl,
                      int hbSource,
                      int hbSourceVideo,
                      Map<Integer, String> iabCategories,
                      JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.hbSource = hbSource;
        this.hbSourceVideo = hbSourceVideo;
        this.iabCategories = ObjectUtils.defaultIfNull(iabCategories, Collections.emptyMap());
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final String defaultDisplayManagerVer = defaultDisplayManagerVer(bidRequest);
        final SameValueValidator<Integer> memberIdValidator = SameValueValidator.create();
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> updatedImps = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpMsft extImp = parseImpExt(imp);

                final Integer memberId = extImp.getMember();
                if (memberId != null && memberIdValidator.isInvalid(memberId)) {
                    errors.add(BidderError.badInput("member id mismatch: all impressions must use the same member id"));
                    return Result.withErrors(errors);
                }

                updatedImps.add(updateImp(imp, extImp, defaultDisplayManagerVer));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (updatedImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final String requestUrl;
        final BidRequest updatedBidRequest;
        try {
            requestUrl = makeRequestUrl(memberIdValidator.getValue());
            updatedBidRequest = updateBidRequest(bidRequest);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return Result.withErrors(errors);
        }

        return Result.of(splitHttpRequests(updatedBidRequest, updatedImps, requestUrl), errors);
    }

    private String defaultDisplayManagerVer(BidRequest request) {
        final Optional<ExtAppPrebid> prebid = Optional.ofNullable(request.getApp())
                .map(App::getExt)
                .map(ExtApp::getPrebid);

        final String source = prebid.map(ExtAppPrebid::getSource).orElse(null);
        final String version = prebid.map(ExtAppPrebid::getVersion).orElse(null);

        return ObjectUtils.allNotNull(source, version)
                ? "%s-%s".formatted(source, version)
                : StringUtils.EMPTY;
    }

    private ExtImpMsft parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), MSFT_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Failed to deserialize Microsoft imp extension: " + e.getMessage());
        }
    }

    private Imp updateImp(Imp imp, ExtImpMsft extImp, String defaultDisplayManagerVer) {
        final String invCode = extImp.getInvCode();
        final Banner banner = imp.getBanner();
        final String displayManagerVer = StringUtils.defaultIfEmpty(
                imp.getDisplaymanagerver(), defaultDisplayManagerVer);

        return imp.toBuilder()
                .tagid(StringUtils.isNotEmpty(invCode) ? invCode : imp.getTagid())
                .banner(banner != null ? updateBanner(banner, extImp) : null)
                .displaymanagerver(displayManagerVer)
                .ext(updateImpExt(imp, extImp))
                .build();
    }

    private Banner updateBanner(Banner banner, ExtImpMsft extImp) {
        final List<Format> bannerFormat = banner.getFormat();

        final Banner.BannerBuilder bannerBuilder = banner.toBuilder();
        if (banner.getW() == null && banner.getH() == null && CollectionUtils.isNotEmpty(bannerFormat)) {
            final Format firstFormat = bannerFormat.getFirst();
            bannerBuilder.w(firstFormat.getW());
            bannerBuilder.h(firstFormat.getH());
        }

        return bannerBuilder
                .api(ObjectUtils.defaultIfNull(banner.getApi(), extImp.getBannerFrameworks()))
                .build();
    }

    private ObjectNode updateImpExt(Imp imp, ExtImpMsft extImp) {
        final MsftExtImpOutgoing impExtOutgoing = MsftExtImpOutgoing.builder()
                .placementId(extImp.getPlacementId())
                .allowSmallerSizes(extImp.getAllowSmallerSizes())
                .usePmtRule(extImp.getUsePmtRule())
                .keywords(extImp.getKeywords())
                .trafficSourceCode(extImp.getTrafficSourceCode())
                .pubClick(extImp.getPubclick())
                .extInvCode(extImp.getExtInvCode())
                .extImpId(extImp.getExtImpId())
                .build();
        final String gpid = imp.getExt().at("/gpid").textValue();

        final ObjectNode updatedImpExt = mapper.mapper().createObjectNode()
                .set("appnexus", mapper.mapper().valueToTree(impExtOutgoing));

        if (StringUtils.isNotEmpty(gpid)) {
            updatedImpExt.put("gpid", gpid);
        }

        return updatedImpExt;
    }

    private String makeRequestUrl(Integer member) {
        try {
            return member != null
                    ? new URIBuilder(endpointUrl).addParameter("member_id", member.toString()).build().toString()
                    : endpointUrl;
        } catch (URISyntaxException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private BidRequest updateBidRequest(BidRequest bidRequest) {
        return bidRequest.toBuilder()
                .ext(updateRequestExt(bidRequest.getExt()))
                .build();
    }

    private ExtRequest updateRequestExt(ExtRequest requestExt) {
        final ExtRequest updatedRequestExt = copyRequestExt(requestExt);
        updatedRequestExt.addProperty("appnexus", updateRequestExtMsft(requestExt, parseRequestExt(requestExt)));
        return updatedRequestExt;
    }

    private ExtRequest copyRequestExt(ExtRequest requestExt) {
        if (requestExt == null) {
            return ExtRequest.empty();
        }

        final ExtRequest newRequestExt = ExtRequest.of(requestExt.getPrebid());
        mapper.fillExtension(newRequestExt, requestExt);
        return newRequestExt;
    }

    private ExtRequestMsft parseRequestExt(ExtRequest requestExt) {
        return Optional.ofNullable(requestExt)
                .map(ext -> ext.getProperty("appnexus"))
                .map(this::parseRequestExtMsft)
                .orElse(null);
    }

    private ExtRequestMsft parseRequestExtMsft(JsonNode extRequestMsftRaw) {
        try {
            return mapper.mapper().treeToValue(extRequestMsftRaw, ExtRequestMsft.class);
        } catch (IllegalArgumentException | JsonProcessingException e) {
            throw new PreBidException("malformed request ext.appnexus");
        }
    }

    private JsonNode updateRequestExtMsft(ExtRequest requestExt, ExtRequestMsft requestExtMsft) {
        final String endpointUrl = extractEndpointUrl(requestExt);

        final ExtRequestMsft.ExtRequestMsftBuilder updatedRequestExtMsftBuilder = Optional.ofNullable(requestExtMsft)
                .map(ExtRequestMsft::toBuilder)
                .orElseGet(ExtRequestMsft::builder)
                .isAmp(BooleanUtils.toInteger(StringUtils.equals(endpointUrl, Endpoint.openrtb2_amp.value())))
                .hbSource(StringUtils.equals(endpointUrl, Endpoint.openrtb2_video.value()) ? hbSourceVideo : hbSource);

        Optional.ofNullable(requestExt)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getTargeting)
                .map(ExtRequestTargeting::getIncludebrandcategory)
                .ifPresent(v -> {
                    updatedRequestExtMsftBuilder.brandCategoryUniqueness(true);
                    updatedRequestExtMsftBuilder.includeBrandCategory(true);
                });

        return mapper.mapper().valueToTree(updatedRequestExtMsftBuilder.build());
    }

    private String extractEndpointUrl(ExtRequest requestExt) {
        return Optional.ofNullable(requestExt)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getServer)
                .map(ExtRequestPrebidServer::getEndpoint)
                .orElse(null);
    }

    private List<HttpRequest<BidRequest>> splitHttpRequests(BidRequest bidRequest,
                                                            List<Imp> imps,
                                                            String requestUrl) {

        return ListUtils.partition(imps, MAX_IMPS_PER_REQUEST)
                .stream()
                .map(impsForRequest -> makeHttpRequest(bidRequest, impsForRequest, requestUrl))
                .toList();
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest bidRequest, List<Imp> imps, String requestUrl) {
        return BidderUtil.defaultRequest(
                bidRequest.toBuilder().imp(imps).build(),
                HttpUtil.headers().add(HttpUtil.X_OPENRTB_VERSION_HEADER, OPENRTB_VERSION),
                requestUrl,
                mapper);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;
        try {
            bidResponse = decodeBodyToBidResponse(httpCall);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        return bidsFromResponse(bidResponse);
    }

    private BidResponse decodeBodyToBidResponse(BidderCall<BidRequest> httpCall) {
        try {
            return mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException("Failed to parse response as BidResponse: " + e.getMessage());
        }
    }

    private Result<List<BidderBid>> bidsFromResponse(BidResponse bidResponse) {
        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bidderBids = Stream.ofNullable(bidResponse)
                .map(BidResponse::getSeatbid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBid(bid, bidResponse, errors))
                .filter(Objects::nonNull)
                .toList();

        if (bidderBids.isEmpty()) {
            errors.add(BidderError.badServerResponse("No valid bids found in response"));
        }

        return Result.of(bidderBids, errors);
    }

    private BidderBid makeBid(Bid bid, BidResponse bidResponse, List<BidderError> errors) {
        try {
            final MsftBidExt extBidMsft = Optional.ofNullable(bid.getExt())
                    .map(ext -> ext.get("appnexus"))
                    .map(this::parseExtBidMsft)
                    .orElseThrow(() -> new PreBidException("Missing Microsoft bid extension"));

            return BidderBid.builder()
                    .bid(bid.toBuilder().cat(getBidCategories(bid, extBidMsft)).build())
                    .type(getBidType(extBidMsft))
                    .bidCurrency(bidResponse.getCur())
                    .dealPriority(extBidMsft.getDealPriority())
                    .videoInfo(makeVideoInfo(extBidMsft))
                    .build();
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }
    }

    private MsftBidExt parseExtBidMsft(JsonNode extBidMsftRaw) {
        try {
            return mapper.mapper().treeToValue(extBidMsftRaw, MsftBidExt.class);
        } catch (IllegalArgumentException | JsonProcessingException e) {
            throw new PreBidException("Failed to deserialize Microsoft bid extension: " + e.getMessage());
        }
    }

    private List<String> getBidCategories(Bid bid, MsftBidExt bidExtMsft) {
        return Optional.ofNullable(bidExtMsft.getBrandCategoryId())
                .map(iabCategories::get)
                .map(Collections::singletonList)
                .orElseGet(() -> {
                    final List<String> cat = bid.getCat();
                    // create empty categories array to force bid to be rejected
                    return cat != null && cat.size() > 1 ? Collections.emptyList() : cat;
                });
    }

    private BidType getBidType(MsftBidExt extBidMsft) {
        final int bidAdType = extBidMsft.getBidAdType();
        return switch (bidAdType) {
            case 0 -> BidType.banner;
            case 1 -> BidType.video;
            case 3 -> BidType.xNative;
            default -> throw new PreBidException("Unsupported bid ad type: " + bidAdType);
        };
    }

    private static ExtBidPrebidVideo makeVideoInfo(MsftBidExt extBidMsft) {
        final int duration = Optional.ofNullable(extBidMsft)
                .map(MsftBidExt::getCreativeInfo)
                .map(MsftBidExtCreative::getVideo)
                .map(MsftBidExtVideo::getDuration)
                .orElse(0);

        return ExtBidPrebidVideo.of(duration, null);
    }
}
