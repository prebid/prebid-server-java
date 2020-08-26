package org.prebid.server.bidder.mobilefuse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
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
import org.prebid.server.proto.openrtb.ext.request.mobilefuse.ExtImpMobilefuse;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * MobilefuseBidder {@link Bidder} implementation.
 */
public class MobilefuseBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpMobilefuse>> MOBILEFUSE_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpMobilefuse>>() {
            };

    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public MobilefuseBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> imps = new ArrayList<>();

        ExtImpMobilefuse firstExtImpMobilefuse = null;
        for (Imp imp : request.getImp()) {
            try {
                final ExtImpMobilefuse extImpMobilefuse = parseImpExt(imp);
                firstExtImpMobilefuse = firstExtImpMobilefuse == null ? extImpMobilefuse : firstExtImpMobilefuse;
                final Imp modifiedImp = modifyImp(imp, extImpMobilefuse);
                imps.add(modifiedImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (firstExtImpMobilefuse == null) {
            return Result.emptyWithError(BidderError.badInput("Invalid ExtImpMobilefuse value"));
        }

        final BidRequest outgoingRequest = request.toBuilder().imp(imps).build();
        final String body = mapper.encode(outgoingRequest);

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(makeUrl(firstExtImpMobilefuse))
                        .headers(HttpUtil.headers())
                        .payload(outgoingRequest)
                        .body(body)
                        .build()),
                errors);
    }

    private ExtImpMobilefuse parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), MOBILEFUSE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private Imp modifyImp(Imp imp, ExtImpMobilefuse extImpMobilefuse) {
        Imp.ImpBuilder impBuilder = imp.toBuilder();

        if (imp.getBanner() != null || imp.getVideo() != null) {
            if (imp.getBanner() != null && imp.getVideo() != null) {
                impBuilder.video(null);
            }

            impBuilder
                    .tagid(String.valueOf(extImpMobilefuse.getPlacementId()))
                    .ext(null);
        }
        return impBuilder.build();
    }

    private String makeUrl(ExtImpMobilefuse extImpMobilefuse) {
        final String baseUrl = String.format("%s%s", endpointUrl, extImpMobilefuse.getPublisherId());
        return Objects.equals(extImpMobilefuse.getTagidSrc(), "ext")
                ? String.format("%s%s", baseUrl, "&tagid_src=ext")
                : baseUrl;
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
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
                .map(bid -> BidderBid.of(bid, getBidType(bidRequest.getImp()), DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    protected BidType getBidType(List<Imp> imps) {
        return imps.get(0).getVideo() != null ? BidType.video : BidType.banner;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
