package org.prebid.server.bidder.eplanning;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderUtil;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.eplanning.ExtImpEplanning;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Eplanning {@link Bidder} implementation.
 */
public class EplanningBidder implements Bidder<BidRequest> {

    private static final String DEFAULT_EXCHANGE_ID = "5a1ad71d2d53a0f5";
    private static final TypeReference<ExtPrebid<?, ExtImpEplanning>> EPLANNING_EXT_TYPE_REFERENCE = new
            TypeReference<ExtPrebid<?, ExtImpEplanning>>() {
            };

    private final String endpointUrlTemplate;

    public EplanningBidder(String endpointUrl) {
        this.endpointUrlTemplate = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl)).concat("/%s");
    }

    /**
     * Creates POST HTTP requests which should be made to fetch bids.
     * <p>
     * One post request will be created for each exchange id value with it's corresponding list of impressions
     */
    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final Map<String, List<Imp>> exchangeIdsToImps = mapExchangeIdsToImps(request.getImp(), errors);
        final MultiMap headers = createHeaders(request.getDevice());
        return createHttpRequests(request, exchangeIdsToImps, headers, errors);
    }

    /**
     * Creates {@link HttpRequest}s one for each exchange id with headers from {@link Device} properties and
     * exchangeId as a part of url.
     */
    private Result<List<HttpRequest<BidRequest>>> createHttpRequests(BidRequest bidRequest,
                                                                     Map<String, List<Imp>> exchangeIdsToImps,
                                                                     MultiMap headers, List<BidderError> errors) {
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>(exchangeIdsToImps.size());
        for (final Map.Entry<String, List<Imp>> exchangeIdToImps : exchangeIdsToImps.entrySet()) {
            final BidRequest exchangeBidRequest = bidRequest.toBuilder().imp(exchangeIdToImps.getValue()).build();
            final String bidRequestBody;
            try {
                bidRequestBody = Json.encode(exchangeBidRequest);
            } catch (EncodeException e) {
                errors.add(BidderError.badInput(String.format("error while encoding bidRequest, err: %s",
                        e.getMessage())));
                return Result.of(Collections.emptyList(), errors);
            }
            httpRequests.add(HttpRequest.of(HttpMethod.POST,
                    String.format(endpointUrlTemplate, exchangeIdToImps.getKey()), bidRequestBody, headers,
                    exchangeBidRequest));
        }
        return Result.of(httpRequests, errors);
    }

    /**
     * Validates and creates {@link Map} where exchangeId is used as key and {@link List} of {@link Imp} as value.
     */
    private static Map<String, List<Imp>> mapExchangeIdsToImps(List<Imp> imps, List<BidderError> errors) {
        final Map<String, List<Imp>> exchangeIdsToImp = new HashMap<>();

        for (final Imp imp : imps) {
            if (imp.getBanner() == null) {
                errors.add(BidderError.badInput(String.format(
                        "EPlanning only supports banner Imps. Ignoring Imp ID=%s", imp.getId())));
                continue;
            }

            final ExtImpEplanning extImpEplanning;
            try {
                extImpEplanning = Json.mapper.<ExtPrebid<?, ExtImpEplanning>>convertValue(imp.getExt(),
                        EPLANNING_EXT_TYPE_REFERENCE).getBidder();
            } catch (IllegalArgumentException ex) {
                errors.add(BidderError.badInput(String.format(
                        "Ignoring imp id=%s, error while decoding extImpBidder, err: %s", imp.getId(),
                        ex.getMessage())));
                continue;
            }

            if (extImpEplanning == null) {
                errors.add(BidderError.badInput(String.format(
                        "Ignoring imp id=%s, error while decoding extImpBidder, err: bidder property is not present",
                        imp.getId())));
                continue;
            }

            final String impExtExchangeId = extImpEplanning.getExchangeId();
            final String exchangeId = StringUtils.isEmpty(impExtExchangeId) ? DEFAULT_EXCHANGE_ID : impExtExchangeId;
            final List<Imp> exchangeIdImps = exchangeIdsToImp.get(exchangeId);

            if (exchangeIdImps == null) {
                exchangeIdsToImp.put(exchangeId, new ArrayList<>(Collections.singleton(imp)));
            } else {
                exchangeIdImps.add(imp);
            }
        }
        return exchangeIdsToImp;
    }

    /**
     * Crates http headers from {@link Device} properties.
     */
    private static MultiMap createHeaders(Device device) {
        final MultiMap headers = BidderUtil.headers();
        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpHeaders.USER_AGENT.toString(), device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpHeaders.ACCEPT_LANGUAGE.toString(), device.getLanguage());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER.toString(), device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.DNT_HEADER.toString(),
                    Objects.toString(device.getDnt(), null));
        }
        return headers;
    }

    /**
     * Converts response to {@link List} of {@link BidderBid}s with {@link List} of errors.
     * Handles cases when response status is different to OK 200.
     */
    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = Json.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return extractBids(bidResponse);
        } catch (DecodeException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    /**
     * Extracts bids from {@link BidResponse} and creates {@link List<BidderBid>} from them.
     */
    private static Result<List<BidderBid>> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }
        return Result.of(bidResponse.getSeatbid().stream()
                        .flatMap(seatBid -> seatBid.getBid().stream())
                        .map(bid -> BidderBid.of(bid, BidType.banner, null))
                        .collect(Collectors.toList()),
                Collections.emptyList());
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
