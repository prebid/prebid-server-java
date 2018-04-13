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
import org.prebid.server.bidder.openx.model.BidRequestWithError;
import org.prebid.server.bidder.openx.model.OpenxImpType;
import org.prebid.server.bidder.openx.proto.ExtImpOpenx;
import org.prebid.server.bidder.openx.proto.OpenxImpExt;
import org.prebid.server.bidder.openx.proto.OpenxRequestExt;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * OpenX {@link Bidder} implementation.
 */
public class OpenxBidder implements Bidder<BidRequest> {

    private static final Logger logger = LoggerFactory.getLogger(OpenxBidder.class);

    private static final String OPENX_CONFIG = "hb_pbs_1.0.0";
    private static final Function<Imp, OpenxImpType> TYPE_RESOLVER = imp -> imp.getBanner() != null
            ? OpenxImpType.banner : imp.getVideo() != null ? OpenxImpType.video : OpenxImpType.other;

    private static final TypeReference<ExtPrebid<?, ExtImpOpenx>> OPENX_EXT_TYPE_REFERENCE = new
            TypeReference<ExtPrebid<?, ExtImpOpenx>>() {
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
                .collect(Collectors.groupingBy(TYPE_RESOLVER, Collectors.toList()));
        final List<BidRequestWithError> bidRequestWithErrors = bidRequestsWithErrors(bidRequest, differentiatedImps);
        final List<BidderError> errors = errors(differentiatedImps.get(OpenxImpType.other), bidRequestWithErrors);

        return Result.of(createRequests(bidRequestWithErrors), errors);
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall httpCall, BidRequest bidRequest) {
        try {
            return Result.of(extractBids(bidRequest, BidderUtil.parseResponse(httpCall.getResponse())),
                    Collections.emptyList());
        } catch (PreBidException e) {
            return Result.of(Collections.emptyList(), Collections.singletonList(BidderError.create(e.getMessage())));
        }
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return null;
    }

    private List<BidRequestWithError> bidRequestsWithErrors(BidRequest bidRequest,
                                                            Map<OpenxImpType, List<Imp>> differentiatedImps) {
        final List<BidRequestWithError> bidRequestWithErrors = new ArrayList<>();
        // single request for all banner imps
        bidRequestWithErrors.add(createSingleRequest(differentiatedImps.get(OpenxImpType.banner), bidRequest));

        final List<Imp> videoImps = differentiatedImps.get(OpenxImpType.video);
        if (CollectionUtils.isNotEmpty(videoImps)) {
            // single request for each video imp
            bidRequestWithErrors.addAll(videoImps.stream()
                    .map(Collections::singletonList)
                    .map(imp -> createSingleRequest(imp, bidRequest))
                    .collect(Collectors.toList()));
        }

        return bidRequestWithErrors;
    }

    private List<BidderError> errors(List<Imp> notSupportedImps, List<BidRequestWithError> bidRequestWithErrors) {
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
        errors.addAll(
                bidRequestWithErrors.stream()
                        .map(BidRequestWithError::getErrors)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList()));

        return errors.stream()
                .map(BidderError::create)
                .collect(Collectors.toList());
    }

    private List<HttpRequest<BidRequest>> createRequests(List<BidRequestWithError> bidRequestWithErrorsAll) {
        return bidRequestWithErrorsAll.stream()
                .map(BidRequestWithError::getBidRequest)
                .filter(Objects::nonNull)
                .map(singleBidRequest -> HttpRequest.of(HttpMethod.POST, endpointUrl,
                        Json.encode(singleBidRequest), BidderUtil.headers(), singleBidRequest))
                .collect(Collectors.toList());
    }

    private BidRequestWithError createSingleRequest(List<Imp> imps, BidRequest bidRequest) {

        if (CollectionUtils.isEmpty(imps)) {
            return BidRequestWithError.of(null, Collections.emptyList());
        }

        final List<String> errors = new ArrayList<>();
        List<Imp> processedImps = null;

        try {
            processedImps = imps.stream().map(this::makeImp).collect(Collectors.toList());
        } catch (PreBidException e) {
            errors.add(e.getMessage());
        }

        final BidRequest ongoingRequest = CollectionUtils.isNotEmpty(processedImps)
                ? bidRequest.toBuilder().imp(processedImps).ext(makeReqExt(imps)).build()
                : null;

        return BidRequestWithError.of(ongoingRequest, errors);
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
        final ExtImpOpenx impExtOpenx;
        if (impExt == null) {
            throw new PreBidException("openx parameters section is missing");
        }

        try {
            impExtPrebid = Json.mapper.convertValue(impExt, OPENX_EXT_TYPE_REFERENCE);
            impExtOpenx = impExtPrebid != null ? impExtPrebid.getBidder() : null;
            if (impExtOpenx == null) {
                throw new PreBidException("openx parameters section is missing");
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Error occurred parsing openx parameters", e);
            throw new PreBidException(e.getMessage(), e);
        }
        return impExtOpenx;
    }

    private ObjectNode makeImpExt(Map<String, String> customParams) {
        return customParams != null
                ? Json.mapper.valueToTree(OpenxImpExt.of(Collections.unmodifiableMap(customParams)))
                : null;
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse == null || bidResponse.getSeatbid() == null
                ? Collections.emptyList()
                : bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        final Map<String, BidType> impidToBidType = impidToBidType(bidRequest);

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, bidType(bid, impidToBidType)))
                .collect(Collectors.toList());
    }

    private static Map<String, BidType> impidToBidType(BidRequest bidRequest) {
        return bidRequest.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, imp -> imp.getBanner() != null ? BidType.banner : BidType.video));
    }

    private static BidType bidType(Bid bid, Map<String, BidType> impidToBidType) {
        return impidToBidType.getOrDefault(bid.getImpid(), BidType.banner);
    }
}
