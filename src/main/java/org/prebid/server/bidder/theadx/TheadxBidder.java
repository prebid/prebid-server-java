package org.prebid.server.bidder.theadx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
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
import org.prebid.server.proto.openrtb.ext.request.theadx.ExtImpTheadx;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class TheadxBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpTheadx>> EXT_TYPE_REFERENCE = new TypeReference<>() {
    };

    private static final TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>> EXT_PREBID_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String OPENRTB_VERSION = "2.5";
    private static final String X_TEST_HEADER = "X-TEST";
    private static final String X_TEST = "1";
    private static final String X_DEVICE_USER_AGENT_HEADER = "X-Device-User-Agent";
    private static final String X_REAL_IP_HEADER = "X-Real-IP";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public TheadxBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> modifiedImps = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpTheadx extImp = parseImpExt(imp);
                modifiedImps.add(imp.toBuilder().tagid(extImp.getTagId()).build());
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final BidRequest modifiedRequest = request.toBuilder().imp(modifiedImps).build();
        return Result.of(Collections.singletonList(makeHttpRequest(modifiedRequest)), errors);
    }

    private ExtImpTheadx parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Missing bidder ext in impression with id: " + imp.getId());
        }
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(this.endpointUrl)
                .impIds(BidderUtil.impIds(request))
                .headers(makeHeaders(request))
                .payload(request)
                .body(mapper.encodeToBytes(request))
                .build();
    }

    private static MultiMap makeHeaders(BidRequest bidRequest) {
        final Device device = bidRequest.getDevice();
        final MultiMap headers = HttpUtil.headers();

        headers.set(HttpUtil.X_OPENRTB_VERSION_HEADER, OPENRTB_VERSION);
        headers.set(X_TEST_HEADER, X_TEST);

        final Optional<Device> optionalDevice = Optional.ofNullable(device);

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, X_DEVICE_USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, X_REAL_IP_HEADER, device.getIpv6());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, X_REAL_IP_HEADER, device.getIp());
        }

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || bidResponse.getSeatbid() == null) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, errors);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<BidderError> errors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> makeBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid makeBid(Bid bid, String currency, List<BidderError> errors) {
        final BidType mediaType = getMediaType(bid, errors);
        return mediaType == null ? null : BidderBid.of(bid, mediaType, currency);
    }

    private BidType getMediaType(Bid bid, List<BidderError> errors) {
        try {
            return Optional.ofNullable(bid.getExt())
                    .map(ext -> mapper.mapper().convertValue(ext, EXT_PREBID_TYPE_REFERENCE))
                    .map(ExtPrebid::getPrebid)
                    .map(ExtBidPrebid::getType)
                    .orElseThrow(IllegalArgumentException::new);
        } catch (IllegalArgumentException e) {
            errors.add(BidderError.badServerResponse(
                    "Failed to parse impression \"%s\" mediatype".formatted(bid.getImpid())));
            return null;
        }
    }
}
