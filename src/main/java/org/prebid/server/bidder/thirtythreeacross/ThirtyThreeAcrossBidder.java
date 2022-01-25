package org.prebid.server.bidder.thirtythreeacross;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
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
import org.prebid.server.bidder.thirtythreeacross.proto.ThirtyThreeAcrossExtTtx;
import org.prebid.server.bidder.thirtythreeacross.proto.ThirtyThreeAcrossExtTtxCaller;
import org.prebid.server.bidder.thirtythreeacross.proto.ThirtyThreeAcrossImpExt;
import org.prebid.server.bidder.thirtythreeacross.proto.ThirtyThreeAcrossImpExtTtx;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.thirtythreeacross.ExtImpThirtyThreeAcross;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ThirtyThreeAcrossBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpThirtyThreeAcross>> THIRTY_THREE_ACROSS_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final ThirtyThreeAcrossExtTtxCaller PREBID_CALLER =
            ThirtyThreeAcrossExtTtxCaller.of("Prebid-Server-Java", "n/a");
    private static final JsonPointer BID_MEDIA_TYPE_POINTER = JsonPointer.valueOf("/ttx/mediaType");

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ThirtyThreeAcrossBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final Map<String, List<Imp>> impsMap = new HashMap<>();
        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();

        final BidRequest modifiedRequest = modifyRequest(request, errors);
        for (Imp imp : modifiedRequest.getImp()) {
            try {
                validateImp(imp);
                final ExtImpThirtyThreeAcross extImpThirtyThreeAcross = parseImpExt(imp);
                final Imp updatedImp = updateImp(imp, extImpThirtyThreeAcross);
                impsMap.computeIfAbsent(computeKey(updatedImp), k -> new ArrayList<>()).add(updatedImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        for (List<Imp> imps : impsMap.values()) {
            requests.add(createRequest(modifiedRequest, imps));
        }

        return Result.of(requests, errors);
    }

    private BidRequest modifyRequest(BidRequest request, List<BidderError> errors) {
        try {
            return request.toBuilder().ext(modifyRequestExt(request.getExt())).build();
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return request;
        }
    }

    private ExtRequest modifyRequestExt(ExtRequest ext) throws PreBidException {
        final Map<String, JsonNode> properties = ObjectUtil.getIfNotNull(ext, ExtRequest::getProperties);
        final JsonNode extTtxNode = ObjectUtil.getIfNotNull(properties, p -> p.get("ttx"));
        final ThirtyThreeAcrossExtTtx extTtx;
        try {
            extTtx = mapper.mapper().convertValue(extTtxNode, ThirtyThreeAcrossExtTtx.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }

        final ExtRequest modifiedExt = ExtRequest.empty();
        if (properties != null) {
            modifiedExt.addProperties(properties);
        }
        modifiedExt.addProperty("ttx", mapper.mapper().valueToTree(modifyRequestExtTtx(extTtx)));

        return modifiedExt;
    }

    private static ThirtyThreeAcrossExtTtx modifyRequestExtTtx(ThirtyThreeAcrossExtTtx extTtx) {
        final List<ThirtyThreeAcrossExtTtxCaller> callers;
        if (extTtx == null || CollectionUtils.isEmpty(extTtx.getCaller())) {
            callers = Collections.singletonList(PREBID_CALLER);
        } else {
            callers = new ArrayList<>(extTtx.getCaller());
            callers.add(PREBID_CALLER);
        }

        return ThirtyThreeAcrossExtTtx.of(callers);
    }

    private void validateImp(Imp imp) throws PreBidException {
        if (imp.getBanner() == null && imp.getVideo() == null) {
            throw new PreBidException(
                    String.format("Imp ID %s must have at least one of [Banner, Video] defined", imp.getId()));
        }
    }

    private ExtImpThirtyThreeAcross parseImpExt(Imp imp) throws PreBidException {
        try {
            return mapper.mapper().convertValue(imp.getExt(), THIRTY_THREE_ACROSS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Imp updateImp(Imp imp, ExtImpThirtyThreeAcross extImpThirtyThreeAcross) throws PreBidException {
        final String productId = extImpThirtyThreeAcross.getProductId();
        return imp.toBuilder()
                .video(updatedVideo(imp.getVideo(), productId))
                .ext(createImpExt(productId, extImpThirtyThreeAcross.getZoneId(), extImpThirtyThreeAcross.getSiteId()))
                .build();
    }

    private static Video updatedVideo(Video video, String productId) throws PreBidException {
        if (video == null) {
            return null;
        }
        if (isZeroOrNullInteger(video.getW())
                || isZeroOrNullInteger(video.getH())
                || CollectionUtils.isEmpty(video.getProtocols())
                || CollectionUtils.isEmpty(video.getMimes())
                || CollectionUtils.isEmpty(video.getPlaybackmethod())) {
            throw new PreBidException("One or more invalid or missing video field(s) "
                    + "w, h, protocols, mimes, playbackmethod");
        }

        return video.toBuilder()
                .startdelay(resolveStartDelay(video.getStartdelay(), productId))
                .placement(resolvePlacement(video.getPlacement(), productId))
                .build();
    }

    private static boolean isZeroOrNullInteger(Integer integer) {
        return integer == null || integer == 0;
    }

    private static Integer resolveStartDelay(Integer startDelay, String productId) {
        return Objects.equals(productId, "instream") ? Integer.valueOf(0) : startDelay;
    }

    private static Integer resolvePlacement(Integer videoPlacement, String productId) {
        if (Objects.equals(productId, "instream")) {
            return 1;
        }
        if (isZeroOrNullInteger(videoPlacement)) {
            return 2;
        }
        return videoPlacement;
    }

    private ObjectNode createImpExt(String productId, String zoneId, String siteId) {
        final ThirtyThreeAcrossImpExt thirtyThreeAcrossImpExt = ThirtyThreeAcrossImpExt.of(
                ThirtyThreeAcrossImpExtTtx.of(productId, StringUtils.isNotEmpty(zoneId) ? zoneId : siteId));
        return mapper.mapper().valueToTree(thirtyThreeAcrossImpExt);
    }

    private String computeKey(Imp imp) {
        final JsonNode ttx = imp.getExt().get("ttx");
        return ttx.get("prod").asText() + ttx.get("zoneid").asText();
    }

    private HttpRequest<BidRequest> createRequest(BidRequest request, List<Imp> requestImps) {
        final BidRequest modifiedRequest = request.toBuilder()
                .imp(requestImps)
                .build();

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(HttpUtil.headers())
                .payload(modifiedRequest)
                .body(mapper.encodeToBytes(modifiedRequest))
                .build();
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
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidType(Bid bid) {
        final String mediaType = ObjectUtil.getIfNotNull(bid.getExt(),
                ext -> ext.at(BID_MEDIA_TYPE_POINTER).asText(null));
        return "video".equals(mediaType) ? BidType.video : BidType.banner;
    }
}
