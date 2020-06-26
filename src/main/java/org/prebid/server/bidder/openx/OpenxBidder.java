package org.prebid.server.bidder.openx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
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
import org.prebid.server.bidder.openx.model.OpenxImpType;
import org.prebid.server.bidder.openx.proto.OpenxImpExt;
import org.prebid.server.bidder.openx.proto.OpenxRequestExt;
import org.prebid.server.bidder.openx.proto.OpenxVideoExt;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.openx.ExtImpOpenx;
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
 * OpenX {@link Bidder} implementation.
 */
public class OpenxBidder implements Bidder<BidRequest> {

    private static final String OPENX_CONFIG = "hb_pbs_1.0.0";
    private static final String DEFAULT_BID_CURRENCY = "USD";

    private static final TypeReference<ExtPrebid<ExtImpPrebid, ExtImpOpenx>> OPENX_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<ExtImpPrebid, ExtImpOpenx>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public OpenxBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final Map<OpenxImpType, List<Imp>> differentiatedImps = bidRequest.getImp().stream()
                .collect(Collectors.groupingBy(OpenxBidder::resolveImpType));

        final List<BidderError> processingErrors = new ArrayList<>();
        final List<BidRequest> outgoingRequests = makeRequests(bidRequest,
                differentiatedImps.get(OpenxImpType.banner),
                differentiatedImps.get(OpenxImpType.video), processingErrors);

        final List<BidderError> errors = errors(differentiatedImps.get(OpenxImpType.other), processingErrors);

        return Result.of(createHttpRequests(outgoingRequests), errors);
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidRequest, bidResponse), Collections.emptyList());
        } catch (DecodeException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }

    private List<BidRequest> makeRequests(BidRequest bidRequest, List<Imp> bannerImps, List<Imp> videoImps,
                                          List<BidderError> errors) {
        final List<BidRequest> bidRequests = new ArrayList<>();
        // single request for all banner imps
        bidRequests.add(createSingleRequest(bannerImps, bidRequest, errors));

        if (CollectionUtils.isNotEmpty(videoImps)) {
            // single request for each video imp
            bidRequests.addAll(videoImps.stream()
                    .map(Collections::singletonList)
                    .map(imps -> createSingleRequest(imps, bidRequest, errors))
                    .collect(Collectors.toList()));
        }
        return bidRequests;
    }

    private static OpenxImpType resolveImpType(Imp imp) {
        return imp.getBanner() != null
                ? OpenxImpType.banner
                : imp.getVideo() != null ? OpenxImpType.video : OpenxImpType.other;
    }

    private List<BidderError> errors(List<Imp> notSupportedImps, List<BidderError> processingErrors) {
        final List<BidderError> errors = new ArrayList<>();
        // add errors for imps with unsupported media types
        if (CollectionUtils.isNotEmpty(notSupportedImps)) {
            errors.addAll(
                    notSupportedImps.stream()
                            .map(imp -> String.format(
                                    "OpenX only supports banner and video imps. Ignoring imp id=%s", imp.getId()))
                            .map(BidderError::badInput)
                            .collect(Collectors.toList()));
        }

        // add errors detected during requests creation
        errors.addAll(processingErrors);

        return errors;
    }

    private List<HttpRequest<BidRequest>> createHttpRequests(List<BidRequest> bidRequests) {
        return bidRequests.stream()
                .filter(Objects::nonNull)
                .map(singleBidRequest -> HttpRequest.<BidRequest>builder().method(HttpMethod.POST).uri(endpointUrl)
                        .body(mapper.encode(singleBidRequest)).headers(HttpUtil.headers()).payload(singleBidRequest)
                        .build())
                .collect(Collectors.toList());
    }

    private BidRequest createSingleRequest(List<Imp> imps, BidRequest bidRequest, List<BidderError> errors) {
        if (CollectionUtils.isEmpty(imps)) {
            return null;
        }

        List<Imp> processedImps = null;
        try {
            processedImps = imps.stream().map(this::makeImp).collect(Collectors.toList());
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
        }

        return CollectionUtils.isNotEmpty(processedImps)
                ? bidRequest.toBuilder()
                    .imp(processedImps)
                    .ext(makeReqExt(imps.get(0)))
                    .build()
                : null;
    }

    private Imp makeImp(Imp imp) {
        final ExtPrebid<ExtImpPrebid, ExtImpOpenx> impExt = parseOpenxExt(imp);
        final ExtImpOpenx openxImpExt = impExt.getBidder();
        final ExtImpPrebid prebidImpExt = impExt.getPrebid();
        final Imp.ImpBuilder impBuilder = imp.toBuilder()
                .tagid(openxImpExt.getUnit())
                .bidfloor(openxImpExt.getCustomFloor())
                .ext(makeImpExt(openxImpExt.getCustomParams()));

        if (resolveImpType(imp) == OpenxImpType.video
                && prebidImpExt != null
                && Objects.equals(prebidImpExt.getIsRewardedInventory(), 1)) {
            impBuilder.video(imp.getVideo().toBuilder()
                                .ext(mapper.mapper().valueToTree(OpenxVideoExt.of(1)))
                                .build());
        }
        return impBuilder.build();
    }

    private ObjectNode makeReqExt(Imp imp) {
        return mapper.mapper().valueToTree(
            OpenxRequestExt.of(parseOpenxExt(imp).getBidder().getDelDomain(), OPENX_CONFIG));
    }

    private ExtPrebid<ExtImpPrebid, ExtImpOpenx> parseOpenxExt(Imp imp) {
        final ObjectNode impExtRaw = imp.getExt();
        final ExtPrebid<ExtImpPrebid, ExtImpOpenx> impExt;
        if (impExtRaw == null) {
            throw new PreBidException("openx parameters section is missing");
        }

        try {
            impExt = mapper.mapper().convertValue(impExtRaw, OPENX_EXT_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }

        final ExtImpOpenx impExtOpenx = impExt != null ? impExt.getBidder() : null;
        if (impExtOpenx == null) {
            throw new PreBidException("openx parameters section is missing");
        }
        return impExt;
    }

    private ObjectNode makeImpExt(Map<String, JsonNode> customParams) {
        return customParams != null
                ? mapper.mapper().valueToTree(OpenxImpExt.of(customParams))
                : null;
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse == null || bidResponse.getSeatbid() == null
                ? Collections.emptyList()
                : bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        final Map<String, BidType> impIdToBidType = impIdToBidType(bidRequest);

        final String bidCurrency = StringUtils.isNotBlank(bidResponse.getCur())
                ? bidResponse.getCur()
                : DEFAULT_BID_CURRENCY;

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, bidType(bid, impIdToBidType), bidCurrency))
                .collect(Collectors.toList());
    }

    private static Map<String, BidType> impIdToBidType(BidRequest bidRequest) {
        return bidRequest.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, imp -> imp.getBanner() != null ? BidType.banner : BidType.video));
    }

    private static BidType bidType(Bid bid, Map<String, BidType> impIdToBidType) {
        return impIdToBidType.getOrDefault(bid.getImpid(), BidType.banner);
    }
}
