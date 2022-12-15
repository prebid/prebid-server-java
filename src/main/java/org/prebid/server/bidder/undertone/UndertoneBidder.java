package org.prebid.server.bidder.undertone;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.undertone.ExtImpUndertone;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class UndertoneBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpUndertone>> UNDERTONE_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final int ADAPTER_ID = 3;
    private static final int VERSION = 1;
    private static final ObjectNode BIDDER_PARAMS = makeBidderParams();
    private final String endpointUrl;
    private final JacksonMapper mapper;

    public UndertoneBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public final Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        if (!ObjectUtils.anyNotNull(bidRequest.getSite(), bidRequest.getApp())) {
            return Result.withError(BidderError.badInput("invalid bidRequest: no App/Site objects"));
        }

        final Map<String, ExtImpUndertone> undertoneExtImpMap = getUndertoneExtImpMap(bidRequest);

        final Integer publisherId = getPublisherId(undertoneExtImpMap);
        if (publisherId == null) {
            return Result.withError(BidderError.badInput("invalid bidRequest: no publisher-id"));
        }

        final List<Imp> imps = makeImps(bidRequest, undertoneExtImpMap);
        if (imps.isEmpty()) {
            return Result.withError(BidderError.badInput("invalid bidRequest: no valid imps"));
        }

        final BidRequest undertoneBidRequest = makeBidRequest(bidRequest, publisherId, imps);

        final HttpRequest<BidRequest> httpRequest = HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(HttpUtil.headers())
                .body(mapper.encodeToBytes(undertoneBidRequest))
                .payload(bidRequest)
                .build();

        return Result.withValue(httpRequest);
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private BidRequest makeBidRequest(BidRequest bidRequest, int publisherId, List<Imp> imps) {
        final Publisher publisher = makePublisher(bidRequest, publisherId);

        final ExtRequestPrebid extRequestPrebid = ExtRequestPrebid.builder()
                .bidderparams(BIDDER_PARAMS)
                .build();

        final BidRequest.BidRequestBuilder bidRequestBuilder = bidRequest.toBuilder()
                .imp(imps)
                .ext(ExtRequest.of(extRequestPrebid));

        if (bidRequest.getSite() != null) {
            bidRequestBuilder.site(bidRequest.getSite()
                    .toBuilder()
                    .publisher(publisher)
                    .build());
        } else {
            bidRequestBuilder.app(bidRequest.getApp()
                    .toBuilder()
                    .publisher(publisher)
                    .build());
        }

        return bidRequestBuilder.build();
    }

    private static ObjectNode makeBidderParams() {
        final ObjectNode objectNode = new ObjectMapper().createObjectNode();
        objectNode.put("id", ADAPTER_ID);
        objectNode.put("version", VERSION);
        return objectNode;
    }

    private Publisher makePublisher(BidRequest bidRequest, int publisherId) {
        final Publisher publisher = bidRequest.getSite() != null
                ? bidRequest.getSite().getPublisher()
                : bidRequest.getApp().getPublisher();

        final Publisher.PublisherBuilder publisherBuilder = publisher == null
                ? Publisher.builder()
                : publisher.toBuilder();

        return publisherBuilder
                .id(String.valueOf(publisherId))
                .build();
    }

    private Integer getPublisherId(Map<String, ExtImpUndertone> extImpMap) {
        return extImpMap.values()
                .stream()
                .map(ExtImpUndertone::getPublisherId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private List<Imp> makeImps(BidRequest bidRequest, Map<String, ExtImpUndertone> extImpMap) {
        return bidRequest.getImp()
                .stream()
                .filter(imp -> isValidImp(imp, extImpMap.get(imp.getId())))
                .map(imp -> imp.toBuilder()
                        .tagid(extImpMap.get(imp.getId()).getPlacementId().toString())
                        .ext(null)
                        .build())
                .toList();
    }

    private Map<String, ExtImpUndertone> getUndertoneExtImpMap(BidRequest bidRequest) {
        return bidRequest.getImp()
                .stream()
                .collect(Collectors.toUnmodifiableMap(Imp::getId,
                        imp -> mapper.mapper().convertValue(imp.getExt(), UNDERTONE_EXT_TYPE_REFERENCE).getBidder()));
    }

    private boolean isValidImp(Imp imp, ExtImpUndertone extImpUndertone) {
        return imp != null && ObjectUtils.anyNotNull(imp.getVideo(), imp.getBanner())
                && extImpUndertone != null && extImpUndertone.getPlacementId() != null;
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return List.of();
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        final Map<String, Imp> idImpMap = getIdImpMap(bidRequest);

        return bidResponse.getSeatbid()
                .stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid, idImpMap), bidResponse.getCur()))
                .toList();
    }

    private Map<String, Imp> getIdImpMap(BidRequest bidRequest) {
        return bidRequest.getImp()
                .stream()
                .collect(Collectors.groupingBy(Imp::getId))
                .entrySet()
                .stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, imps -> imps.getValue().get(0)));
    }

    private BidType getBidType(Bid bid, Map<String, Imp> idImpMap) {
        final Imp imp = idImpMap.get(bid.getImpid());

        if (imp == null || imp.getBanner() != null) {
            return BidType.banner;
        }

        if (imp.getVideo() != null) {
            return BidType.video;
        }

        return BidType.banner;
    }

}

