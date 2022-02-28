package org.prebid.server.bidder.operaads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
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
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Arrays;
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
    private static final String BIDDER_CURRENCY = "USD";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public OperaadsBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        try {
            validateDevice(request.getDevice());
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            final ExtImpOperaads extImpOperaads;
            final Imp modifiedImp;
            try {
                extImpOperaads = parseImpExt(imp);
                modifiedImp = modifyImp(imp, extImpOperaads.getPlacementId());
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }

            if (modifiedImp != null) {
                requests.add(createRequest(request, modifiedImp, extImpOperaads));
            }
        }

        return Result.of(requests, errors);
    }

    private static void validateDevice(Device device) {
        if (device == null || StringUtils.isEmpty(device.getOs())) {
            throw new PreBidException("Request is missing device OS information");
        }
    }

    private ExtImpOperaads parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), OPERAADS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Imp modifyImp(Imp imp, String placementId) {
        final Imp.ImpBuilder impBuilder = imp.toBuilder()
                .tagid(placementId);

        final String impId = imp.getId();
        if (imp.getBanner() != null) {
            impBuilder.id(buildImpId(impId, BidType.banner))
                    .banner(modifyBanner(imp.getBanner()))
                    .video(null)
                    .xNative(null);
        } else if (imp.getVideo() != null) {
            impBuilder.id(buildImpId(impId, BidType.video))
                    .xNative(null);
        } else if (imp.getXNative() != null) {
            impBuilder.id(buildImpId(impId, BidType.xNative))
                    .xNative(modifyNative(imp.getXNative()));
        } else {
            return null;
        }

        return impBuilder.build();
    }

    private static String buildImpId(String originalId, BidType type) {
        return String.format("%s:opa:%s", originalId, type.getName());
    }

    private static Banner modifyBanner(Banner banner) {
        final Integer w = banner.getW();
        final Integer h = banner.getH();
        final List<Format> formats = banner.getFormat();

        if (w == null || w == 0 || h == null || h == 0) {
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

    private Native modifyNative(Native xNative) {
        final JsonNode requestNode;
        try {
            requestNode = mapper.mapper().readTree(xNative.getRequest());
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }

        final JsonNode nativeNode = requestNode != null
                ? requestNode.path("native")
                : MissingNode.getInstance();

        if (nativeNode.isMissingNode()) {
            final JsonNode modifiedRequestNode = mapper.mapper().createObjectNode().set("native", requestNode);
            return xNative.toBuilder()
                    .request(mapper.encodeToString(modifiedRequestNode))
                    .build();
        }

        return xNative;
    }

    private HttpRequest<BidRequest> createRequest(BidRequest bidRequest, Imp imp, ExtImpOperaads extImpOperaads) {
        final BidRequest outgoingRequest = bidRequest.toBuilder()
                .imp(Collections.singletonList(imp))
                .build();

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(resolveUrl(extImpOperaads))
                .headers(HttpUtil.headers())
                .payload(outgoingRequest)
                .body(mapper.encodeToBytes(outgoingRequest))
                .build();
    }

    private String resolveUrl(ExtImpOperaads extImpOperaads) {
        return endpointUrl
                .replace(PUBLISHER_ID_MACRO, HttpUtil.encodeUrl(extImpOperaads.getPublisherId()))
                .replace(ACCOUNT_ID_MACRO, HttpUtil.encodeUrl(extImpOperaads.getEndpointId()));
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(OperaadsBidder::isValidBid)
                .map(this::createBidderBid)
                .collect(Collectors.toList());
    }

    private static boolean isValidBid(Bid bid) {
        return BidderUtil.isValidPrice(ObjectUtil.getIfNotNull(bid, Bid::getPrice));
    }

    private BidderBid createBidderBid(Bid bid) {
        final String[] idParts = StringUtils.split(bid.getImpid(), ":");

        if (idParts == null || idParts.length < 2) {
            throw new PreBidException("BidType not provided.");
        }

        return BidderBid.of(
                modifyBid(bid, constructImpId(idParts)),
                parseBidType(idParts[idParts.length - 1]),
                BIDDER_CURRENCY);
    }

    private static Bid modifyBid(Bid bid, String impId) {
        return bid.toBuilder().impid(impId).build();
    }

    private static String constructImpId(String[] idParts) {
        return Arrays.stream(idParts)
                .limit(idParts.length - 2)
                .collect(Collectors.joining(":"));
    }

    private BidType parseBidType(String bidType) {
        try {
            return mapper.mapper().convertValue(bidType, BidType.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }
}

