package org.prebid.server.bidder.adtelligent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.Json;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderUtil;
import org.prebid.server.bidder.adtelligent.proto.AdtelligentImpExt;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adtelligent.ExtImpAdtelligent;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adtelligent {@link Bidder} implementation.
 */
public class AdtelligentBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdtelligent>> ADTELLIGENT_EXT_TYPE_REFERENCE = new
            TypeReference<ExtPrebid<?, ExtImpAdtelligent>>() {
            };

    private final String endpointUrl;
    private final MultiMap headers;

    public AdtelligentBidder(String endpointUrl) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        headers = BidderUtil.headers();
    }

    /**
     * Creates POST HTTP requests which should be made to fetch bids.
     */
    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final Result<Map<Integer, List<Imp>>> sourceIdToImpsResult = mapSourceIdToImp(request.getImp());
        return createHttpRequests(sourceIdToImpsResult.getValue(), sourceIdToImpsResult.getErrors(), request);
    }

    /**
     * Converts response to {@link List} of {@link BidderBid}s with {@link List} of errors.
     * Handles cases when response status is different to OK 200.
     */
    @Override
    public Result<List<BidderBid>> makeBids(HttpCall httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = Json.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return extractBids(bidResponse, bidRequest.getImp());
        } catch (DecodeException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }

    /**
     * Validates and creates {@link Map} where sourceId is used as key and {@link List} of {@link Imp} as value.
     */
    private Result<Map<Integer, List<Imp>>> mapSourceIdToImp(List<Imp> imps) {
        final List<BidderError> errors = new ArrayList<>();
        final Map<Integer, List<Imp>> sourceToImps = new HashMap<>();
        for (final Imp imp : imps) {
            final ExtImpAdtelligent extImpAdtelligent;
            try {
                validateImpression(imp);
                extImpAdtelligent = getExtImpAdtelligent(imp);
            } catch (PreBidException ex) {
                errors.add(BidderError.badInput(ex.getMessage()));
                continue;
            }
            final Imp updatedImp = updateImp(imp, extImpAdtelligent);

            final Integer sourceId = extImpAdtelligent.getSourceId();
            final List<Imp> sourceIdImps = sourceToImps.get(sourceId);
            if (sourceIdImps == null) {
                sourceToImps.put(sourceId, new ArrayList<>(Collections.singleton(updatedImp)));
            } else {
                sourceIdImps.add(updatedImp);
            }
        }
        return Result.of(sourceToImps, errors);
    }

    /**
     * Creates {@link HttpRequest}s. One for each source id. Adds source id as url parameter
     */
    private Result<List<HttpRequest<BidRequest>>> createHttpRequests(Map<Integer, List<Imp>> sourceToImps,
                                                                     List<BidderError> errors, BidRequest request) {
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        for (Map.Entry<Integer, List<Imp>> sourceIdToImps : sourceToImps.entrySet()) {
            final String url = String.format("%s?aid=%d", endpointUrl, sourceIdToImps.getKey());
            final BidRequest bidRequest = request.toBuilder().imp(sourceIdToImps.getValue()).build();
            final String bidRequestBody;
            try {
                bidRequestBody = Json.encode(bidRequest);
            } catch (EncodeException e) {
                errors.add(BidderError.badInput(String.format("error while encoding bidRequest, err: %s",
                        e.getMessage())));
                return Result.of(Collections.emptyList(), errors);
            }
            httpRequests.add(HttpRequest.of(HttpMethod.POST, url, bidRequestBody, headers, bidRequest));
        }
        return Result.of(httpRequests, errors);
    }

    /**
     * Extracts {@link ExtImpAdtelligent} from imp.ext.bidder.
     */
    private ExtImpAdtelligent getExtImpAdtelligent(Imp imp) {
        try {
            return Json.mapper.<ExtPrebid<?, ExtImpAdtelligent>>convertValue(imp.getExt(),
                    ADTELLIGENT_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format(
                    "ignoring imp id=%s, error while decoding impExt, err: %s", imp.getId(), e.getMessage()));
        }
    }

    /**
     * Validates {@link Imp}s. Throws {@link PreBidException} in case of {@link Imp} is invalid.
     */
    private void validateImpression(Imp imp) {
        final String impId = imp.getId();
        if (imp.getBanner() == null && imp.getVideo() == null) {
            throw new PreBidException(String.format(
                    "ignoring imp id=%s, Adtelligent supports only Video and Banner", impId));
        }

        final ObjectNode impExt = imp.getExt();
        if (impExt == null || impExt.size() == 0) {
            throw new PreBidException(String.format("ignoring imp id=%s, extImpBidder is empty", impId));
        }
    }

    /**
     * Updates {@link Imp} with bigfloor if it is present in imp.ext.bidder
     */
    private Imp updateImp(Imp imp, ExtImpAdtelligent extImpAdtelligent) {
        final AdtelligentImpExt adtelligentImpExt = AdtelligentImpExt.of(extImpAdtelligent);
        final Float bidFloor = extImpAdtelligent.getBigFloor();
        return imp.toBuilder()
                .bidfloor(bidFloor != null && bidFloor > 0 ? bidFloor : imp.getBidfloor())
                .ext(Json.mapper.valueToTree(adtelligentImpExt))
                .build();
    }

    /**
     * Extracts {@link Bid}s from response.
     */
    private static Result<List<BidderBid>> extractBids(BidResponse bidResponse, List<Imp> imps) {
        return bidResponse == null || bidResponse.getSeatbid() == null
                ? Result.of(Collections.emptyList(), Collections.emptyList())
                : createBiddersBid(bidResponse, imps);
    }

    /**
     * Extracts {@link Bid}s from response and finds its type against matching {@link Imp}. In case matching {@link Imp}
     * was not found, {@link Bid} is considered as not valid.
     */
    private static Result<List<BidderBid>> createBiddersBid(BidResponse bidResponse, List<Imp> imps) {

        final Map<String, Imp> idToImps = imps.stream().collect(Collectors.toMap(Imp::getId, Function.identity()));
        final List<BidderBid> bidderBids = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .forEach(bid -> addBidOrError(bid, idToImps, bidderBids, errors));

        return Result.of(bidderBids, errors);
    }

    /**
     * Creates {@link BidderBid} from {@link Bid} if it has matching {@link Imp}, otherwise adds error to error list.
     */
    private static void addBidOrError(Bid bid, Map<String, Imp> idToImps, List<BidderBid> bidderBids,
                                      List<BidderError> errors) {
        final String bidImpId = bid.getImpid();

        if (idToImps.containsKey(bidImpId)) {
            final Video video = idToImps.get(bidImpId).getVideo();
            bidderBids.add(BidderBid.of(bid, video != null ? BidType.video : BidType.banner, null));
        } else {
            errors.add(BidderError.badServerResponse(String.format(
                    "ignoring bid id=%s, request doesn't contain any impression with id=%s", bid.getId(), bidImpId)));
        }
    }
}
