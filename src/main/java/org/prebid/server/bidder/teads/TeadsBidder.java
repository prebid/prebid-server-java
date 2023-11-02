package org.prebid.server.bidder.teads;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.prebid.server.proto.openrtb.ext.request.teads.TeadsImpExt;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TeadsBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, TeadsImpExt>> TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>> EXT_PREBID_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public TeadsBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        try {
            final List<Imp> modifiedImps = request.getImp().stream()
                    .map(this::modifyImp)
                    .collect(Collectors.toList());
            final HttpRequest<BidRequest> httpRequest = makeHttpRequest(request.toBuilder().imp(modifiedImps).build());
            return Result.withValue(httpRequest);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }
    }

    private Imp modifyImp(Imp imp) {
        final TeadsImpExt impExt = parseImpExt(imp);
        if (Objects.equals(impExt.getPlacementId(), 0)) {
            throw new PreBidException("placementId should not be 0");
        }
        final ObjectNode modifiedImpExt = mapper.mapper().convertValue(TeadsImpExtKV.of(impExt), ObjectNode.class);

        return imp.toBuilder()
                .tagid(String.valueOf(impExt.getPlacementId()))
                .banner(modifyBanner(imp.getBanner()))
                .ext(modifiedImpExt)
                .build();
    }

    private static Banner modifyBanner(Banner banner) {
        if (banner != null) {
            final List<Format> format = banner.getFormat();
            if (CollectionUtils.isNotEmpty(format)) {
                final Format firstFormat = format.get(0);
                return banner.toBuilder().w(firstFormat.getW()).h(firstFormat.getH()).build();
            }
        }

        return banner;
    }

    private TeadsImpExt parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest bidRequest) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(HttpUtil.headers())
                .impIds(BidderUtil.impIds(bidRequest))
                .body(mapper.encodeToBytes(bidRequest))
                .payload(bidRequest)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse("Bad Server Response"));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("Empty SeatBid array");
        }

        final Map<String, Imp> impMap = bidRequest.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, Function.identity()));

        return bidResponse.getSeatbid()
                .stream()
                .flatMap(seatBid -> Optional.ofNullable(seatBid.getBid()).orElse(List.of()).stream())
                .map(bid -> makeBid(impMap, bid, bidResponse.getCur()))
                .toList();
    }

    private BidderBid makeBid(Map<String, Imp> impMap, Bid bid, String currency) {
        final BidType bidType = Optional.ofNullable(impMap.get(bid.getImpid()))
                .map(imp -> imp.getVideo() == null ? BidType.banner : BidType.video)
                .orElseThrow(() -> new PreBidException("Bid for the Imp " + bid.getImpid() + " wasn't found"));

        final ExtBidPrebidMeta meta = parseExtBidPrebidMeta(bid);
        if (StringUtils.isBlank(meta.getRendererName())) {
            throw new PreBidException("RendererName should not be empty");
        }

        if (StringUtils.isBlank(meta.getRendererVersion())) {
            throw new PreBidException("RendererVersion should not be empty");
        }

        return BidderBid.of(bid, bidType, currency);

    }

    private ExtBidPrebidMeta parseExtBidPrebidMeta(Bid bid) {
        try {
            return mapper.mapper().convertValue(bid.getExt(), EXT_PREBID_TYPE_REFERENCE).getPrebid().getMeta();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

}

