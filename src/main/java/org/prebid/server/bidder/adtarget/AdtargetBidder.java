package org.prebid.server.bidder.adtarget;

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
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.adtarget.proto.AdtargetImpExt;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adtarget.ExtImpAdtarget;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
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
 * Adtarget {@link Bidder} implementation.
 */
public class AdtargetBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdtarget>> ADTARGET_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAdtarget>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    private final MultiMap headers;

    public AdtargetBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
        headers = HttpUtil.headers();
    }

    /**
     * Creates POST HTTP requests which should be made to fetch bids.
     */
    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final Result<Map<Integer, List<Imp>>> sourceIdToImpsResult = mapSourceIdToImp(request.getImp());

        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        for (Map.Entry<Integer, List<Imp>> sourceIdToImps : sourceIdToImpsResult.getValue().entrySet()) {
            final String url = String.format("%s?aid=%d", endpointUrl, sourceIdToImps.getKey());
            final BidRequest bidRequest = request.toBuilder().imp(sourceIdToImps.getValue()).build();
            final String bidRequestBody = mapper.encode(bidRequest);
            httpRequests.add(HttpRequest.<BidRequest>builder()
                    .method(HttpMethod.POST)
                    .uri(url)
                    .body(bidRequestBody)
                    .headers(headers)
                    .payload(bidRequest)
                    .build());
        }
        return Result.of(httpRequests, sourceIdToImpsResult.getErrors());
    }

    /**
     * Validates and creates {@link Map} where sourceId is used as key and {@link List} of {@link Imp} as value.
     */
    private Result<Map<Integer, List<Imp>>> mapSourceIdToImp(List<Imp> imps) {
        final List<BidderError> errors = new ArrayList<>();
        final Map<Integer, List<Imp>> sourceToImps = new HashMap<>();
        for (Imp imp : imps) {
            final ExtImpAdtarget extImpAdtarget;
            try {
                validateImpression(imp);
                extImpAdtarget = parseImpAdtarget(imp);
            } catch (PreBidException ex) {
                errors.add(BidderError.badInput(ex.getMessage()));
                continue;
            }
            final Imp updatedImp = updateImp(imp, extImpAdtarget);

            final Integer sourceId = extImpAdtarget.getSourceId();
            sourceToImps.computeIfAbsent(sourceId, ignored -> new ArrayList<>()).add(updatedImp);
        }
        return Result.of(sourceToImps, errors);
    }

    /**
     * Extracts {@link ExtImpAdtarget} from imp.ext.bidder.
     */
    private ExtImpAdtarget parseImpAdtarget(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ADTARGET_EXT_TYPE_REFERENCE).getBidder();
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
                    "ignoring imp id=%s, Adtarget supports only Video and Banner", impId));
        }

        final ObjectNode impExt = imp.getExt();
        if (impExt == null || impExt.size() == 0) {
            throw new PreBidException(String.format("ignoring imp id=%s, extImpBidder is empty", impId));
        }
    }

    /**
     * Updates {@link Imp} with bidfloor if it is present in imp.ext.bidder
     */
    private Imp updateImp(Imp imp, ExtImpAdtarget extImpAdtarget) {
        final AdtargetImpExt adtargetImpExt = AdtargetImpExt.of(extImpAdtarget);
        final BigDecimal bidFloor = extImpAdtarget.getBidFloor();
        return imp.toBuilder()
                .bidfloor(bidFloor != null && bidFloor.compareTo(BigDecimal.ZERO) > 0 ? bidFloor : imp.getBidfloor())
                .ext(mapper.mapper().valueToTree(adtargetImpExt))
                .build();
    }

    /**
     * Converts response to {@link List} of {@link BidderBid}s with {@link List} of errors.
     */
    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return extractBids(bidResponse, bidRequest.getImp());
        } catch (DecodeException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
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
                .forEach(bid -> addBidOrError(bid, idToImps, bidderBids, errors, bidResponse.getCur()));

        return Result.of(bidderBids, errors);
    }

    /**
     * Creates {@link BidderBid} from {@link Bid} if it has matching {@link Imp}, otherwise adds error to error list.
     */
    private static void addBidOrError(Bid bid, Map<String, Imp> idToImps, List<BidderBid> bidderBids,
                                      List<BidderError> errors, String currency) {
        final String bidImpId = bid.getImpid();

        if (idToImps.containsKey(bidImpId)) {
            final Video video = idToImps.get(bidImpId).getVideo();
            bidderBids.add(BidderBid.of(bid, video != null ? BidType.video : BidType.banner, currency));
        } else {
            errors.add(BidderError.badServerResponse(String.format(
                    "ignoring bid id=%s, request doesn't contain any impression with id=%s", bid.getId(), bidImpId)));
        }
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
