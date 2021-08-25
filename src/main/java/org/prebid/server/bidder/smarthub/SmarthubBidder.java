package org.prebid.server.bidder.smarthub;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.smarthub.ExtImpSmarthub;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

public class SmarthubBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSmarthub>> SMARTHUB_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpSmarthub>>() {
            };
    private static final String ADAPTER_VER = "1.0.0";
    private final String endpointTemplate;
    private final JacksonMapper mapper;

    public SmarthubBidder(String endpointTemplate, JacksonMapper mapper) {
        this.endpointTemplate = HttpUtil.validateUrl(Objects.requireNonNull(endpointTemplate));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> imps = request.getImp();
        ExtImpSmarthub extImpSmarthub;
        try {
            extImpSmarthub = parseAndValidateImpExt(imps.get(0));

        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return Result.withErrors(errors);
        }

        return Result.withValue(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .body(mapper.encode(request))
                .uri(buildEndpointUrl(extImpSmarthub))
                .headers(resolveHeaders())
                .build());
    }

    private MultiMap resolveHeaders() {
        final MultiMap headers = HttpUtil.headers();
        headers.add("Prebid-Adapter-Ver", ADAPTER_VER);

        return headers;
    }

    private String buildEndpointUrl(ExtImpSmarthub extImpSmarthub) {
        return endpointTemplate.replace("{{HOST}}", extImpSmarthub.getPartnerName())
                .replace("{{AccountID}}", extImpSmarthub.getSeat())
                .replace("{{SourceId}}", extImpSmarthub.getToken());
    }

    private ExtImpSmarthub parseAndValidateImpExt(Imp imp) {
        final ExtImpSmarthub extImpSmarthub;
        try {
            extImpSmarthub = mapper.mapper().convertValue(imp.getExt(), SMARTHUB_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new PreBidException(e.getMessage());
        }
        if (StringUtils.isBlank(extImpSmarthub.getPartnerName())) {
            throw new PreBidException("partnerName parameter is required for smarthub bidder");
        }
        if (StringUtils.isBlank(extImpSmarthub.getSeat())) {
            throw new PreBidException("seat parameter is required for smarthub bidder");
        }
        if (StringUtils.isBlank(extImpSmarthub.getToken())) {
            throw new PreBidException("token parameter is required for smarthub bidder");
        }
        return extImpSmarthub;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        ArrayList<BidderError> errors = new ArrayList<>();
        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode == HttpURLConnection.HTTP_NO_CONTENT) {
            return Result.withError(BidderError.badServerResponse("HTTP_NO_CONTENT"));
        } else if (statusCode == HttpURLConnection.HTTP_BAD_REQUEST) {
            return Result.withError(BidderError.badServerResponse("HTTP_BAD_REQUEST"));
        } else if (statusCode == HttpURLConnection.HTTP_UNAVAILABLE) {
            return Result.withError(BidderError.badServerResponse("HTTP_UNAVAILABLE"));
        } else if (statusCode != HttpURLConnection.HTTP_OK) {
            return Result.withError(BidderError.badServerResponse("Status code: " + statusCode));
        }

        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("array SeatBid cannot be empty");
        }
        return bidsFromResponse(bidRequest, bidResponse, errors);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse, List<BidderError> errors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> constructBidderBid(bid, bidResponse, errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private BidderBid constructBidderBid(Bid bid, BidResponse bidResponse, List<BidderError> errors) {
        try {
            return BidderBid.of(bid, getBidType(bid.getExt()), bidResponse.getCur());
        } catch (IllegalArgumentException | PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }
    }

    private BidType getBidType(ObjectNode bidExt) {
        final JsonNode typeNode = bidExt != null && !bidExt.isEmpty() ? bidExt.get("mediaType") : null;
        if (typeNode == null || !typeNode.isTextual()) {
            throw new PreBidException("missing bid ext");
        }
        return mapper.mapper().convertValue(typeNode.asText(), BidType.class);
    }

}
