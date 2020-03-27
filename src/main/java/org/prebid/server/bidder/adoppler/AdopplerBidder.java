package org.prebid.server.bidder.adoppler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.adoppler.model.AdopplerResponseExt;
import org.prebid.server.bidder.adoppler.model.AdopplerResponseVideoAdsExt;
import org.prebid.server.bidder.adoppler.model.AdopplerResponseVideoExt;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adoppler.ExtImpAdoppler;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AdopplerBidder implements Bidder<BidRequest> {
    private static final TypeReference<ExtPrebid<?, ExtImpAdoppler>> ADOPPLER_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAdoppler>>() {
            };

    private static final String DEFAULT_BID_CURRENCY = "USD";
    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AdopplerBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> result = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpAdoppler validExtImp = parseAndValidateImpExt(imp);
                final String updateRequestId = request.getId() + "-" + validExtImp.getAdunit();
                final BidRequest updateRequest = request.toBuilder().id(updateRequestId).build();
                final String url = endpointUrl + "/processHeaderBid/" + validExtImp.getAdunit();
                result.add(createSingleRequest(imp, updateRequest, url));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        return Result.of(result, errors);
    }

    private ExtImpAdoppler parseAndValidateImpExt(Imp imp) {
        final ExtImpAdoppler extImpAdoppler;
        try {
            extImpAdoppler = mapper.mapper().convertValue(imp.getExt(), ADOPPLER_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
        if (StringUtils.isBlank(extImpAdoppler.getAdunit())) {
            throw new PreBidException("$.imp.ext.adoppler.adunit required");
        }
        return extImpAdoppler;
    }

    private HttpRequest<BidRequest> createSingleRequest(Imp imp, BidRequest request, String url) {
        final BidRequest outgoingRequest = request.toBuilder().imp(Collections.singletonList(imp)).build();
        final String body = mapper.encode(outgoingRequest);
        final MultiMap headers = HttpUtil.headers().add("x-openrtb-version", "2.5");
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(url)
                .headers(headers)
                .body(body)
                .payload(outgoingRequest)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        } else if (statusCode == HttpResponseStatus.BAD_REQUEST.code()) {
            return Result.emptyWithError(BidderError.badInput("bad request"));
        } else if (statusCode != HttpResponseStatus.OK.code()) {
            return Result.emptyWithError(BidderError.badServerResponse(String.format("Unexpected HTTP status %s.",
                    statusCode)));
        }

        final BidResponse bidResponse;
        try {
            bidResponse = decodeBodyToBidResponse(httpCall);
        } catch (PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(String.format("invalid body: %s",
                    e.getMessage())));
        }
        final Map<String, BidType> impTypes;
        final List<BidderBid> bidderBids;
        try {
            impTypes = getImpTypes(bidRequest);
            bidderBids = getBids(bidResponse, impTypes);
        } catch (PreBidException e) {
            return Result.emptyWithError(BidderError.badInput(e.getMessage()));
        }
        return Result.of(bidderBids, Collections.emptyList());
    }

    private BidResponse decodeBodyToBidResponse(HttpCall<BidRequest> httpCall) {
        try {
            return mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private AdopplerResponseExt parseResponseExt(ObjectNode ext) {
        try {
            return mapper.mapper().treeToValue(ext, AdopplerResponseExt.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private Map<String, BidType> getImpTypes(BidRequest bidRequest) {
        final Map<String, BidType> impTypes = new HashMap<>();
        for (Imp imp : bidRequest.getImp()) {
            if (impTypes.get(imp.getId()) != null) {
                throw new PreBidException(String.format("duplicate $.imp.id %s", imp.getId()));
            }
            if (imp.getBanner() != null) {
                impTypes.put(imp.getId(), BidType.banner);
            } else if (imp.getVideo() != null) {
                impTypes.put(imp.getId(), BidType.video);
            } else if (imp.getAudio() != null) {
                impTypes.put(imp.getId(), BidType.audio);
            } else if (imp.getXNative() != null) {
                impTypes.put(imp.getId(), BidType.xNative);
            } else {
                throw new PreBidException("one of $.imp.banner, $.imp.video, $.imp.audio "
                        + "and $.imp.native field required");
            }
        }
        return impTypes;
    }

    private List<BidderBid> getBids(BidResponse bidResponse, Map<String, BidType> impTypes) {
        final List<BidderBid> bidderBids = new ArrayList<>();
        for (SeatBid seatBid : bidResponse.getSeatbid()) {
            for (Bid bid : seatBid.getBid()) {
                if (impTypes.get(bid.getImpid()) == null) {
                    throw new PreBidException(String.format("unknown impid: %s", bid.getImpid()));
                }
                setExtBidPrebidVideo(bid, impTypes);
                final BidderBid bidderBid = BidderBid.of(bid, impTypes.get(bid.getImpid()), DEFAULT_BID_CURRENCY);
                bidderBids.add(bidderBid);
            }
        }
        return bidderBids;
    }

    private ExtBidPrebidVideo setExtBidPrebidVideo(Bid bid, Map<String, BidType> impTypes) {
        final AdopplerResponseExt adopplerResponseExt;
        if (impTypes.get(bid.getImpid()) == BidType.video) {
            final ObjectNode ext = bid.getExt();
            try {
                adopplerResponseExt = parseResponseExt(ext);
            } catch (PreBidException e) {
                throw new PreBidException(e.getMessage());
            }
            final AdopplerResponseVideoExt adopplerResponseVideoExt = adopplerResponseExt.getAds();
            if (adopplerResponseVideoExt == null) {
                throw new PreBidException("$.seatbid.bid.ext.ads.video required");
            }
            final AdopplerResponseVideoAdsExt responseVideoAdsExt = adopplerResponseVideoExt.getVideo();
            final Integer duration = responseVideoAdsExt != null ? responseVideoAdsExt.getDuration() : null;
            return ExtBidPrebidVideo.of(duration, head(bid.getCat()));
        }
        return null;
    }

    private String head(List<String> cat) {
        if (cat.size() == 0) {
            return "";
        }
        return cat.get(0);
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
