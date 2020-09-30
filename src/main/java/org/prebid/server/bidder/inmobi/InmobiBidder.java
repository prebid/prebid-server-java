package org.prebid.server.bidder.inmobi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
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
import org.prebid.server.proto.openrtb.ext.request.inmobi.ExtImpInmobi;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class InmobiBidder implements Bidder<BidRequest> {
    private static final TypeReference<ExtPrebid<?, ExtImpInmobi>> INMOBI_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpInmobi>>() {
            };

    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public InmobiBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();

        if (request.getImp().size() == 0) {
            BidderError.badInput("No impression in the request");
        }

        Imp imp = request.getImp().get(0);

        ExtImpInmobi extImpInmobi;
        try {
            extImpInmobi = parseImpExt(imp);
        } catch (Exception e) {
            return Result.emptyWithError(BidderError.badInput("bad InMobi bidder ext"));
        }

        if (extImpInmobi.getPlc().length() == 0) {
            return Result.emptyWithError(BidderError.badInput("'plc' is a required attribute for InMobi's bidder ext"));
        }

        if (imp.getBanner() != null) {
            Banner banner = imp.getBanner();
            if ((banner.getW() == null || banner.getH() == null || banner.getW() == 0
                    || banner.getH() == 0) && banner.getFormat().size() > 0) {
                Format format = banner.getFormat().get(0);
                banner.toBuilder().w(format.getW()).h(format.getH());
            }
        }

        final BidRequest outgoingRequest = request.toBuilder().imp(request.getImp()).build();

        final String body = mapper.encode(outgoingRequest);

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .headers(HttpUtil.headers())
                        .payload(outgoingRequest)
                        .body(body)
                        .build()),
                errors);
    }

    private ExtImpInmobi parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), INMOBI_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        } else if (statusCode != HttpResponseStatus.OK.code()) {
            return Result.emptyWithError(BidderError.badServerResponse(String.format("Unexpected http status code: %s.",
                    statusCode)));
        }

        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || bidResponse.getSeatbid() == null) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    protected BidType getBidType(String impId, List<Imp> imps) {
        BidType mediaType = BidType.banner;
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getVideo() != null) {
                    mediaType = BidType.video;
                    break;
                }
            }
        }
        return mediaType;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
