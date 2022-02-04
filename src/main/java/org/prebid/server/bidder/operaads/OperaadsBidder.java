package org.prebid.server.bidder.operaads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.prebid.server.proto.openrtb.ext.request.operaads.ExtImpOperaads;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class OperaadsBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpOperaads>> OPERAADS_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String PUBLISHER_ID_MACRO = "{{PublisherId}}";
    private static final String ACCOUNT_ID_MACRO = "{{AccountId}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public OperaadsBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        try {
            checkRequest(request);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpOperaads extImpOperaads = parseImpExt(imp);
                final Imp resolvedImp = resolveImp(imp, extImpOperaads);

                requests.add(createRequest(request, resolvedImp, extImpOperaads));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(requests, errors);
    }

    private static void checkRequest(BidRequest request) {
        final Device device = request.getDevice();
        if (device == null || StringUtils.isEmpty(device.getOs())) {
            throw new PreBidException("Request is missing device OS information");
        }
    }

    private ExtImpOperaads parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), OPERAADS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("Missing bidder ext in impression with id: %s", imp.getId()));
        }
    }

    private Imp resolveImp(Imp imp, ExtImpOperaads extImpOperaads) {
        final Banner banner = imp.getBanner();
        final Native xNative = imp.getXNative();
        final boolean isNativeResolvable = xNative != null && StringUtils.isNotEmpty(xNative.getRequest());
        return imp.toBuilder()
                .xNative(isNativeResolvable ? resolveNative(xNative) : xNative)
                .banner(banner != null ? resolveBanner(banner) : null)
                .tagid(extImpOperaads.getPlacementId())
                .build();
    }

    private Native resolveNative(Native xNative) {
        try {
            final JsonNode nativeNode = mapper.mapper().readTree(xNative.getRequest()).get("native");
            if (nativeNode != null && nativeNode.isObject()) {
                final JsonNode requestNode = mapper.mapper().createObjectNode().set("native", nativeNode);
                return xNative.toBuilder()
                        .request(mapper.encodeToString(requestNode))
                        .build();
            }
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }
        return xNative;
    }

    private static Banner resolveBanner(Banner banner) {
        final Integer w = banner.getW();
        final Integer h = banner.getH();
        final List<Format> formats = banner.getFormat();
        if (w == null || h == null || w == 0 || h == 0) {
            if (CollectionUtils.isNotEmpty(formats)) {
                final Format firstFormat = formats.get(0);
                return banner.toBuilder()
                        .w(firstFormat.getW())
                        .h(firstFormat.getH())
                        .build();
            }

            throw new PreBidException("Size information missing for banner");
        }
        return banner;
    }

    private HttpRequest<BidRequest> createRequest(BidRequest bidRequest, Imp imp, ExtImpOperaads extImpOperaads) {
        final String resolvedUrl = endpointUrl
                .replace(PUBLISHER_ID_MACRO, HttpUtil.encodeUrl(extImpOperaads.getPublisherId()))
                .replace(ACCOUNT_ID_MACRO, HttpUtil.encodeUrl(extImpOperaads.getEndpointId()));

        final BidRequest outgoingRequest = bidRequest.toBuilder()
                .imp(Collections.singletonList(imp))
                .build();

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(resolvedUrl)
                .headers(HttpUtil.headers())
                .payload(outgoingRequest)
                .body(mapper.encodeToBytes(outgoingRequest))
                .build();
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(OperaadsBidder::isBidValid)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()),
                        bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static boolean isBidValid(Bid bid) {
        final BigDecimal price = bid.getPrice();
        return price != null && price.compareTo(BigDecimal.ZERO) > 0;
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (impId.equals(imp.getId())) {
                if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
                return BidType.banner;
            }
        }
        return BidType.banner;
    }
}

