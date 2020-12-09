package org.prebid.server.bidder.adoppler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.adoppler.model.AdopplerResponseExt;
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
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Adoppler {@link Bidder} implementation.
 */
public class AdopplerBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdoppler>> ADOPPLER_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAdoppler>>() {
            };
    private static final String DEFAULT_CLIENT = "app";

    private final String endpointTemplate;
    private final JacksonMapper mapper;

    public AdopplerBidder(String endpointTemplate, JacksonMapper mapper) {
        this.endpointTemplate = HttpUtil.validateUrl(Objects.requireNonNull(endpointTemplate));
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
                final String url = resolveUrl(validExtImp);

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
            throw new PreBidException(e.getMessage());
        }
        if (StringUtils.isBlank(extImpAdoppler.getAdunit())) {
            throw new PreBidException("$.imp.ext.adoppler.adunit required");
        }
        return extImpAdoppler;
    }

    private String resolveUrl(ExtImpAdoppler extImp) {
        final String client = extImp.getClient();

        try {
            final String accountIdMacro = StringUtils.isBlank(client)
                    ? DEFAULT_CLIENT
                    : HttpUtil.encodeUrl(client);

            return endpointTemplate
                    .replace("{{AccountID}}", accountIdMacro)
                    .replace("{{AdUnit}}", HttpUtil.encodeUrl(extImp.getAdunit()));
        } catch (Exception e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private HttpRequest<BidRequest> createSingleRequest(Imp imp, BidRequest request, String url) {
        final BidRequest outgoingRequest = request.toBuilder().imp(Collections.singletonList(imp)).build();
        final String body = mapper.encode(outgoingRequest);
        final MultiMap headers = HttpUtil.headers().add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");
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
        try {
            final BidResponse bidResponse = decodeBodyToBidResponse(httpCall);
            final Map<String, BidType> impTypes = getImpTypes(bidRequest);
            final List<BidderBid> bidderBids = bidResponse.getSeatbid().stream()
                    .filter(Objects::nonNull)
                    .map(SeatBid::getBid)
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream)
                    .map(bid -> createBid(bid, impTypes, bidResponse.getCur()))
                    .collect(Collectors.toList());
            return Result.withValues(bidderBids);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }
    }

    private BidResponse decodeBodyToBidResponse(HttpCall<BidRequest> httpCall) {
        try {
            return mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(String.format("invalid body: %s", e.getMessage()));
        }
    }

    private Map<String, BidType> getImpTypes(BidRequest bidRequest) {
        final Map<String, BidType> impTypes = new HashMap<>();
        for (Imp imp : bidRequest.getImp()) {
            final String impId = imp.getId();
            if (impTypes.get(impId) != null) {
                throw new PreBidException(String.format("duplicate $.imp.id %s", impId));
            }
            if (imp.getBanner() != null) {
                impTypes.put(impId, BidType.banner);
            } else if (imp.getVideo() != null) {
                impTypes.put(impId, BidType.video);
            } else if (imp.getAudio() != null) {
                impTypes.put(impId, BidType.audio);
            } else if (imp.getXNative() != null) {
                impTypes.put(impId, BidType.xNative);
            } else {
                throw new PreBidException("one of $.imp.banner, $.imp.video, $.imp.audio "
                        + "and $.imp.native field required");
            }
        }
        return impTypes;
    }

    private BidderBid createBid(Bid bid, Map<String, BidType> impTypes, String currency) {
        if (impTypes.get(bid.getImpid()) == null) {
            throw new PreBidException(String.format("unknown impId: %s", bid.getImpid()));
        }
        validateResponseVideoExt(bid, impTypes);
        return BidderBid.of(bid, impTypes.get(bid.getImpid()), currency);
    }

    private void validateResponseVideoExt(Bid bid, Map<String, BidType> impTypes) {
        if (impTypes.get(bid.getImpid()) == BidType.video) {
            final ObjectNode ext = bid.getExt();
            final AdopplerResponseExt adopplerResponseExt = parseResponseExt(ext);
            final AdopplerResponseVideoExt adopplerResponseVideoExt = adopplerResponseExt.getAds();
            if (adopplerResponseVideoExt == null) {
                throw new PreBidException("$.seatbid.bid.ext.ads.video required");
            }
        }
    }

    private AdopplerResponseExt parseResponseExt(ObjectNode ext) {
        try {
            return mapper.mapper().treeToValue(ext, AdopplerResponseExt.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }
}
