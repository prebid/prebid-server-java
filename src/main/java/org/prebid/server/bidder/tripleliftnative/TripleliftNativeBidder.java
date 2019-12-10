package org.prebid.server.bidder.tripleliftnative;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisherPrebid;
import org.prebid.server.proto.openrtb.ext.request.triplelift.ExtImpTriplelift;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class TripleliftNativeBidder implements Bidder<BidRequest> {

    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final String UNKONWN_PUBLSIHER_ID = "unknown";

    private static final TypeReference<ExtPrebid<?, ExtImpTriplelift>> TRIPLELIFT_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpTriplelift>>() {
            };
    private static final TypeReference<List<String>> WHITELIST_TYPE_REFERENCE =
            new TypeReference<List<String>>() {
            };

    private final String endpointUrl;
    private final List<String> publisherWhiteList;

    public TripleliftNativeBidder(String endpointUrl, Map<String, String> extraInfo) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.publisherWhiteList = initPublisherWhiteList(extraInfo);
    }

    private static List<String> initPublisherWhiteList(Map<String, String> extraInfo) {
        final String jsonWhiteList = extraInfo == null ? null : extraInfo.get("publisher_whitelist");

        if (jsonWhiteList == null) {
            return Collections.emptyList();
        }

        try {
            final List<String> whiteList = Json.mapper.readValue(jsonWhiteList, WHITELIST_TYPE_REFERENCE);
            return whiteList == null ? Collections.emptyList() : whiteList;
        } catch (JsonProcessingException e) {
            throw new PreBidException("TripleliftNativeBidder could not unmarshal config json");
        }
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> validImps = new ArrayList<>();
        for (Imp imp : bidRequest.getImp()) {
            try {
                validImps.add(modifyImp(imp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final String publisherId = effectivePublisherId(bidRequest);
        if (!publisherWhiteList.contains(publisherId)) {
            errors.add(BidderError.badInput("Unsupported publisher for triplelift_native"));
            return Result.of(Collections.emptyList(), errors);
        }

        if (validImps.isEmpty()) {
            errors.add(BidderError.badInput("No valid impressions for triplelift"));
            return Result.of(Collections.emptyList(), errors);
        }

        final BidRequest updatedRequest = bidRequest.toBuilder()
                .imp(validImps)
                .build();

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .body(Json.encode(updatedRequest))
                        .headers(HttpUtil.headers())
                        .payload(updatedRequest)
                        .build()),
                errors);
    }

    private static Imp modifyImp(Imp imp) throws PreBidException {
        if (imp.getXNative() == null) {
            throw new PreBidException("no native object specified");
        }

        final ExtImpTriplelift impExt = parseExtImpTriplelift(imp);
        final String inventoryCode = impExt.getInventoryCode();
        if (StringUtils.isBlank(inventoryCode)) {
            throw new PreBidException("no inv_code specified");
        }

        return imp.toBuilder()
                .tagid(inventoryCode)
                .bidfloor(impExt.getFloor())
                .build();
    }

    private static ExtImpTriplelift parseExtImpTriplelift(Imp imp) {
        try {
            return Json.mapper.<ExtPrebid<?, ExtImpTriplelift>>convertValue(imp.getExt(),
                    TRIPLELIFT_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static String effectivePublisherId(BidRequest bidRequest) {
        String publisherId = UNKONWN_PUBLSIHER_ID;

        final Publisher publisher = findPublisher(bidRequest);
        if (publisher == null) {
            return publisherId;
        }
        final String id = publisher.getId();
        publisherId = StringUtils.isBlank(id) ? UNKONWN_PUBLSIHER_ID : id;

        final ObjectNode publisherExt = publisher.getExt();
        if (publisherExt != null) {
            final ExtPublisher extPublisher;
            try {
                extPublisher = Json.mapper.convertValue(publisherExt, ExtPublisher.class);
                final ExtPublisherPrebid extPublisherPrebid = extPublisher != null ? extPublisher.getPrebid() : null;
                if (extPublisherPrebid != null && StringUtils.isNotBlank(extPublisherPrebid.getParentAccount())) {
                    return extPublisherPrebid.getParentAccount();
                }
            } catch (IllegalArgumentException ignored) {
                return publisherId;
            }
        }

        return publisherId;
    }

    private static Publisher findPublisher(BidRequest bidRequest) {
        final App app = bidRequest.getApp();
        if (app != null) {
            return app.getPublisher();
        }

        final Site site = bidRequest.getSite();
        if (site != null) {
            return site.getPublisher();
        }

        return null;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final HttpResponse response = httpCall.getResponse();
        if (response.getStatusCode() == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }

        try {
            final BidResponse bidResponse = Json.decodeValue(response.getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        return bidResponse == null || bidResponse.getSeatbid() == null
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, BidType.xNative, DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}

