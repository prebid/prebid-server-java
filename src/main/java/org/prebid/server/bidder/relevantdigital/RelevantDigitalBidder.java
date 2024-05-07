package org.prebid.server.bidder.relevantdigital;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
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
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.request.relevantdigital.ExtImpRelevantDigital;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class RelevantDigitalBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpRelevantDigital>> EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final int MAX_REQUEST_COUNT = 5;
    private static final String X_OPENRTB_VERSION = "2.5";
    private static final String RELEVANT_PROPERTY = "relevant";
    private static final String RELEVANT_COUNT_PROPERTY = "count";
    private static final String RELEVANT_ADAPTER_TYPE_PROPERTY = "adapterType";
    private static final String ADAPTER_TYPE = "server";
    private static final String HOST_MACRO = "{{Host}}";
    private static final long DEFAULT_TMAX = 1000L;
    private static final String EXT_PREBID = "prebid";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public RelevantDigitalBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> modifiedImps = new ArrayList<>();
        ExtImpRelevantDigital impExt = null;

        for (final Imp imp : request.getImp()) {
            try {
                impExt = parseExtImp(imp.getExt());
                modifiedImps.add(modifyImp(imp, impExt));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (modifiedImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final BidRequest modifiedBidRequest;
        try {
            modifiedBidRequest = modifyBidRequest(request, modifiedImps, impExt);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return Result.withErrors(errors);
        }

        return Result.of(Collections.singletonList(makeHttpRequest(modifiedBidRequest, impExt.getPbsHost())), errors);
    }

    private ExtImpRelevantDigital parseExtImp(ObjectNode ext) {
        try {
            return mapper.mapper().convertValue(ext, EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private Imp modifyImp(Imp imp, ExtImpRelevantDigital impExt) {
        Optional.ofNullable(imp.getBanner()).map(Banner::getExt).ifPresent(ext -> ext.remove(RELEVANT_PROPERTY));
        Optional.ofNullable(imp.getAudio()).map(Audio::getExt).ifPresent(ext -> ext.remove(RELEVANT_PROPERTY));
        Optional.ofNullable(imp.getVideo()).map(Video::getExt).ifPresent(ext -> ext.remove(RELEVANT_PROPERTY));
        Optional.ofNullable(imp.getXNative()).map(Native::getExt).ifPresent(ext -> ext.remove(RELEVANT_PROPERTY));

        final ExtImpPrebid modifiedImpExtPrebid = ExtImpPrebid.builder()
                .storedrequest(ExtStoredRequest.of(impExt.getPlacementId()))
                .build();
        final ExtImp modifiedImpExt = ExtImp.of(modifiedImpExtPrebid, null);

        return imp.toBuilder().ext(mapper.mapper().valueToTree(modifiedImpExt)).build();
    }

    private BidRequest modifyBidRequest(BidRequest request, List<Imp> modifiedImps, ExtImpRelevantDigital impExt) {
        return request.toBuilder()
                .imp(modifiedImps)
                .ext(modifyExt(request.getExt(), impExt))
                .tmax(modifyTmax(request.getTmax(), impExt.getPbsBufferMs()))
                .build();
    }

    private ExtRequest modifyExt(ExtRequest originalExt, ExtImpRelevantDigital impExt) {
        final int relevantCount = Optional.ofNullable(originalExt)
                .map(ext -> ext.getProperty(RELEVANT_PROPERTY))
                .map(relevant -> relevant.get(RELEVANT_COUNT_PROPERTY).asInt())
                .orElse(0);

        if (relevantCount >= MAX_REQUEST_COUNT) {
            throw new PreBidException("too many requests");
        }

        final ExtRequestPrebid modifiedPrebid = Optional.ofNullable(originalExt)
                .map(ExtRequest::getPrebid)
                .map(prebid -> prebid.toBuilder().aliases(null).targeting(null).cache(null))
                .orElseGet(ExtRequestPrebid::builder)
                .storedrequest(ExtStoredRequest.of(impExt.getAccountId()))
                .build();
        final ExtRequest modifiedExt = ExtRequest.of(modifiedPrebid);
        final ObjectNode relevantNode = mapper.mapper().createObjectNode()
                .put(RELEVANT_COUNT_PROPERTY, relevantCount + 1)
                .put(RELEVANT_ADAPTER_TYPE_PROPERTY, ADAPTER_TYPE);

        modifiedExt.addProperty(RELEVANT_PROPERTY, relevantNode);

        return modifiedExt;
    }

    private static Long modifyTmax(Long originalTmax, Long bufferTmax) {
        final long timeout = Optional.ofNullable(originalTmax).filter(tmax -> tmax > 0).orElse(DEFAULT_TMAX);
        final long buffer = Optional.ofNullable(bufferTmax).orElse(0L);
        return Math.min(Math.max(timeout - buffer, buffer), timeout);
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest bidRequest, String host) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(makeUrl(host))
                .impIds(BidderUtil.impIds(bidRequest))
                .body(mapper.encodeToBytes(bidRequest))
                .headers(makeHeaders(bidRequest))
                .payload(bidRequest)
                .build();
    }

    private String makeUrl(String host) {
        final String modifiedHost = host
                .replace("http://", "")
                .replace("https://", "")
                .replace(".relevant-digital.com", "");
        return endpointUrl.replace(HOST_MACRO, modifiedHost);
    }

    private static MultiMap makeHeaders(BidRequest bidRequest) {
        final Device device = bidRequest.getDevice();
        final MultiMap headers = HttpUtil.headers();

        headers.set(HttpUtil.X_OPENRTB_VERSION_HEADER, X_OPENRTB_VERSION);
        HttpUtil.addHeaderIfValueIsNotEmpty(
                headers,
                HttpUtil.USER_AGENT_HEADER,
                ObjectUtil.getIfNotNull(device, Device::getUa));
        HttpUtil.addHeaderIfValueIsNotEmpty(
                headers,
                HttpUtil.X_FORWARDED_FOR_HEADER,
                ObjectUtil.getIfNotNull(device, Device::getIpv6));
        HttpUtil.addHeaderIfValueIsNotEmpty(
                headers,
                HttpUtil.X_FORWARDED_FOR_HEADER,
                ObjectUtil.getIfNotNull(device, Device::getIp));

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> errors = new ArrayList<>();
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse("Bad Server Response"));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("Empty SeatBid array");
        }
        return bidResponse.getSeatbid()
                .stream()
                .flatMap(seatBid -> Optional.ofNullable(seatBid.getBid()).orElse(List.of()).stream())
                .map(bid -> makeBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid makeBid(Bid bid, String currency, List<BidderError> errors) {
        final Integer markupType = Optional.ofNullable(bid.getMtype()).orElse(-1);
        try {
            final BidType bidType = switch (markupType) {
                case 1 -> BidType.banner;
                case 2 -> BidType.video;
                case 3 -> BidType.audio;
                case 4 -> BidType.xNative;
                default -> getBidMediaTypeFromExt(bid);
            };

            return BidderBid.of(bid, bidType, currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }
    }

    private BidType getBidMediaTypeFromExt(Bid bid) {
        return Optional.ofNullable(bid.getExt())
                .map(ext -> (ObjectNode) ext.get(EXT_PREBID))
                .map(this::parseExtBidPrebid)
                .map(ExtBidPrebid::getType)
                .orElseThrow(() ->
                        new PreBidException("Failed to parse bid[i].ext.prebid.type for the bid " + bid.getImpid()));
    }

    private ExtBidPrebid parseExtBidPrebid(ObjectNode prebid) {
        try {
            return mapper.mapper().treeToValue(prebid, ExtBidPrebid.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
