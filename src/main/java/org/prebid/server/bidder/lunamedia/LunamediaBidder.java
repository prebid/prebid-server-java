package org.prebid.server.bidder.lunamedia;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.lunamedia.ExtImpLunamedia;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class LunamediaBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpLunamedia>> IMP_EXT_TYPE_REFERENCE = new
            TypeReference<ExtPrebid<?, ExtImpLunamedia>>() {
            };
    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public LunamediaBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        try {
            final Map<ExtImpLunamedia, List<Imp>> impToExtImp = impExtToImps(request.getImp(), errors);
            httpRequests.addAll(buildBidderRequests(request, impToExtImp));
        } catch (PreBidException e) {
            return Result.of(Collections.emptyList(), errors);
        }

        return Result.of(httpRequests, errors);
    }

    private Map<ExtImpLunamedia, List<Imp>> impExtToImps(List<Imp> imps, List<BidderError> errors) {
        final Map<ExtImpLunamedia, List<Imp>> extToListOfUpdatedImp = new HashMap<>();
        for (Imp imp : imps) {
            try {
                final ExtImpLunamedia extImpLunamedia = parseAndValidateImpExt(imp);
                final Imp updatedImp = updateImp(imp);

                extToListOfUpdatedImp.putIfAbsent(extImpLunamedia, new ArrayList<>());
                extToListOfUpdatedImp.get(extImpLunamedia).add(updatedImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (extToListOfUpdatedImp.isEmpty()) {
            throw new PreBidException("No appropriate impressions");
        }

        return extToListOfUpdatedImp;
    }

    private ExtImpLunamedia parseAndValidateImpExt(Imp imp) {
        final ExtImpLunamedia extImpLunamedia;
        try {
            extImpLunamedia = mapper.mapper().convertValue(imp.getExt(), IMP_EXT_TYPE_REFERENCE)
                    .getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        if (StringUtils.isBlank(extImpLunamedia.getPubid())) {
            throw new PreBidException("No pubid value provided");
        }

        return extImpLunamedia;
    }

    private static Imp updateImp(Imp imp) {
        final Imp.ImpBuilder impBuilder = imp.toBuilder().ext(null);

        final Video video = imp.getVideo();
        if (video != null) {
            return impBuilder.banner(null)
                    .audio(null)
                    .xNative(null)
                    .build();
        }

        final Banner banner = imp.getBanner();
        if (banner != null) {
            return impBuilder.banner(modifyImpBanner(banner)).build();
        }

        throw new PreBidException("Unsupported impression has been received");
    }

    private static Banner modifyImpBanner(Banner banner) {
        if (banner.getW() == null || banner.getH() == null) {
            final Banner.BannerBuilder bannerBuilder = banner.toBuilder();
            final List<Format> originalFormat = banner.getFormat();

            if (CollectionUtils.isEmpty(originalFormat)) {
                throw new PreBidException("Expected at least one banner.format entry or explicit w/h");
            }

            final List<Format> formatSkipFirst = originalFormat.subList(1, originalFormat.size());
            bannerBuilder.format(formatSkipFirst);

            Format firstFormat = originalFormat.get(0);
            bannerBuilder.w(firstFormat.getW());
            bannerBuilder.h(firstFormat.getH());

            return bannerBuilder.build();
        }

        return banner;
    }

    private List<HttpRequest<BidRequest>> buildBidderRequests(BidRequest bidRequest,
                                                              Map<ExtImpLunamedia, List<Imp>> impExtToListOfImps) {
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Map.Entry<ExtImpLunamedia, List<Imp>> impExtAndListOfImps : impExtToListOfImps.entrySet()) {
            final ExtImpLunamedia extImpLunamedia = impExtAndListOfImps.getKey();
            final List<Imp> imps = impExtAndListOfImps.getValue();
            final BidRequest updatedBidRequest = makeBidRequest(bidRequest, extImpLunamedia, imps);

            final String body = mapper.encode(updatedBidRequest);
            final MultiMap headers = HttpUtil.headers()
                    .add("x-openrtb-version", "2.5");
            final String createdEndpoint = endpointUrl + extImpLunamedia.getPubid();

            final HttpRequest<BidRequest> createdBidRequest = HttpRequest.<BidRequest>builder()
                    .method(HttpMethod.POST)
                    .uri(createdEndpoint)
                    .body(body)
                    .headers(headers)
                    .payload(bidRequest)
                    .build();

            httpRequests.add(createdBidRequest);
        }

        return httpRequests;
    }

    private static BidRequest makeBidRequest(BidRequest preBidRequest, ExtImpLunamedia extImpLunamedia,
                                             List<Imp> imps) {
        final BidRequest.BidRequestBuilder bidRequestBuilder = preBidRequest.toBuilder();

        final List<Imp> modifiedImps = imps.stream()
                .map(imp -> imp.toBuilder().tagid(extImpLunamedia.getPlacement()).build())
                .collect(Collectors.toList());

        bidRequestBuilder.imp(modifiedImps);

        final Site site = preBidRequest.getSite();
        if (site != null) {
            bidRequestBuilder.site(site.toBuilder().publisher(null).domain("").build());
        }

        final App app = preBidRequest.getApp();
        if (app != null) {
            bidRequestBuilder.app(app.toBuilder().publisher(null).build());
        }
        return bidRequestBuilder.build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        } else if (statusCode == HttpResponseStatus.BAD_REQUEST.code()) {
            return Result.emptyWithError(BidderError.badInput("Invalid request."));
        } else if (statusCode != HttpResponseStatus.OK.code()) {
            return Result.emptyWithError(BidderError.badServerResponse(String.format("Unexpected HTTP status %s.",
                    statusCode)));
        }

        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || bidResponse.getSeatbid() == null) {
            return Collections.emptyList();
        }
        if (bidResponse.getSeatbid().size() != 1) {
            throw new PreBidException(String.format("Invalid SeatBids count: %d", bidResponse.getSeatbid().size()));
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .map(SeatBid::getBid)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getType(bid.getImpid(), bidRequest.getImp()), DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    /**
     * Resolves the media type for the bid.
     */
    private static BidType getType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId) && imp.getVideo() != null) {
                return BidType.video;
            }
        }
        return BidType.banner;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
