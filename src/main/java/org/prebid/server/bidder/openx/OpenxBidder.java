package org.prebid.server.bidder.openx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderUtil;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.openx.model.OpenxImpType;
import org.prebid.server.bidder.openx.proto.OpenxImpExt;
import org.prebid.server.bidder.openx.proto.OpenxRequestExt;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
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

    private static final Logger logger = LoggerFactory.getLogger(OpenxBidder.class);

    private static final String OPENX_CONFIG = "hb_pbs_1.0.0";

    private static final TypeReference<ExtPrebid<?, ExtImpOpenx>> OPENX_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpOpenx>>() {
            };

    private final String endpointUrl;

    public OpenxBidder(String endpointUrl) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<Imp> imps = bidRequest.getImp();
        if (CollectionUtils.isEmpty(imps)) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }

        final Map<OpenxImpType, List<Imp>> differentiatedImps = imps.stream()
                .collect(Collectors.groupingBy(OpenxBidder::resolveImpType));
        final List<String> processingErrors = new ArrayList<>();

        final List<BidRequest> outgoingRequests = makeRequests(bidRequest,
                differentiatedImps.get(OpenxImpType.banner),
                differentiatedImps.get(OpenxImpType.video), processingErrors);

        final List<BidderError> errors = errors(differentiatedImps.get(OpenxImpType.other), processingErrors);

        return Result.of(createHttpRequests(outgoingRequests), errors);
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall httpCall, BidRequest bidRequest) {
        try {
            return Result.of(extractBids(bidRequest, BidderUtil.parseResponse(httpCall.getResponse())),
                    Collections.emptyList());
        } catch (BidderUtil.BadServerResponseException | PreBidException e) {
            return BidderUtil.createEmptyResultWithError(BidderError.Type.bad_server_response, e.getMessage());
        } catch (BidderUtil.BadInputRequestException e) {
            return BidderUtil.createEmptyResultWithError(BidderError.Type.bad_input, e.getMessage());
        }
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }

    private List<BidRequest> makeRequests(BidRequest bidRequest, List<Imp> bannerImps, List<Imp> videoImps,
                                          List<String> errors) {
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

    private List<BidderError> errors(List<Imp> notSupportedImps, List<String> processingErrors) {
        final List<String> errors = new ArrayList<>();
        // add errors for imps with unsupported media types
        if (CollectionUtils.isNotEmpty(notSupportedImps)) {
            errors.addAll(
                    notSupportedImps.stream()
                            .map(imp -> String.format(
                                    "OpenX only supports banner and video imps. Ignoring imp id=%s", imp.getId()))
                            .collect(Collectors.toList()));
        }

        // add errors detected during requests creation
        errors.addAll(processingErrors);

        return BidderUtil.createBidderErrors(BidderError.Type.bad_input, errors);
    }

    private List<HttpRequest<BidRequest>> createHttpRequests(List<BidRequest> bidRequests) {
        return bidRequests.stream()
                .filter(Objects::nonNull)
                .map(singleBidRequest -> HttpRequest.of(HttpMethod.POST, endpointUrl,
                        Json.encode(singleBidRequest), BidderUtil.headers(), singleBidRequest))
                .collect(Collectors.toList());
    }

    private BidRequest createSingleRequest(List<Imp> imps, BidRequest bidRequest, List<String> errors) {
        if (CollectionUtils.isEmpty(imps)) {
            return null;
        }

        List<Imp> processedImps = null;
        try {
            processedImps = imps.stream().map(this::makeImp).collect(Collectors.toList());
        } catch (PreBidException e) {
            errors.add(e.getMessage());
        }

        return CollectionUtils.isNotEmpty(processedImps)
                ? bidRequest.toBuilder().imp(processedImps).ext(makeReqExt(imps)).build()
                : null;
    }

    private Imp makeImp(Imp imp) {
        final ExtImpOpenx openxImpExt = parseOpenxExt(imp);
        return imp.toBuilder()
                .tagid(openxImpExt.getUnit())
                .bidfloor(openxImpExt.getCustomFloor())
                .ext(makeImpExt(openxImpExt.getCustomParams()))
                .build();
    }

    private ObjectNode makeReqExt(List<Imp> imps) {
        return Json.mapper.valueToTree(OpenxRequestExt.of(parseOpenxExt(imps.get(0)).getDelDomain(), OPENX_CONFIG));
    }

    private ExtImpOpenx parseOpenxExt(Imp imp) {
        final ObjectNode impExt = imp.getExt();
        final ExtPrebid<?, ExtImpOpenx> impExtPrebid;
        if (impExt == null) {
            throw new PreBidException("openx parameters section is missing");
        }

        try {
            impExtPrebid = Json.mapper.convertValue(impExt, OPENX_EXT_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            logger.warn("Error occurred parsing openx parameters", e);
            throw new PreBidException(e.getMessage(), e);
        }

        final ExtImpOpenx impExtOpenx = impExtPrebid != null ? impExtPrebid.getBidder() : null;
        if (impExtOpenx == null) {
            throw new PreBidException("openx parameters section is missing");
        }
        return impExtOpenx;
    }

    private ObjectNode makeImpExt(Map<String, String> customParams) {
        return customParams != null
                ? Json.mapper.valueToTree(OpenxImpExt.of(customParams))
                : null;
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse == null || bidResponse.getSeatbid() == null
                ? Collections.emptyList()
                : bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        final Map<String, BidType> impIdToBidType = impIdToBidType(bidRequest);

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, bidType(bid, impIdToBidType), null))
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
