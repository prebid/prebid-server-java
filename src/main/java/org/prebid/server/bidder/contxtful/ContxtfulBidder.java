package org.prebid.server.bidder.contxtful;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.contxtful.request.ContxtfulBidRequest;
import org.prebid.server.bidder.contxtful.request.ContxtfulBidRequestParams;
import org.prebid.server.bidder.contxtful.request.ContxtfulBidderRequest;
import org.prebid.server.bidder.contxtful.request.ContxtfulCompositeRequest;
import org.prebid.server.bidder.contxtful.request.ContxtfulConfig;
import org.prebid.server.bidder.contxtful.request.ContxtfulConfigDetails;
import org.prebid.server.bidder.contxtful.response.ContxtfulBid;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserPrebid;
import org.prebid.server.proto.openrtb.ext.request.contxtful.ExtImpContxtful;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ContxtfulBidder implements Bidder<ContxtfulCompositeRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpContxtful>> TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final String ACCOUNT_ID_MACRO = "{{AccountId}}";
    private static final String BIDDER_NAME = "contxtful";
    private static final String DEFAULT_ADAPTER_VERSION = "v1";
    private static final String DEFAULT_CURRENCY = "USD";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ContxtfulBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<ContxtfulCompositeRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<ContxtfulBidRequest> bidRequests = new ArrayList<>();
        String customerId = null;

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpContxtful extImp = parseImpExt(imp);
                if (customerId == null) {
                    customerId = extImp.getCustomerId();
                }
                bidRequests.add(ContxtfulBidRequest.of(
                        BIDDER_NAME,
                        ContxtfulBidRequestParams.of(extImp.getPlacementId()), imp.getId()));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (CollectionUtils.isEmpty(bidRequests)) {
            return Result.withErrors(errors);
        }

        final ContxtfulCompositeRequest outgoingRequest = ContxtfulCompositeRequest.builder()
                .ortb2Request(request.toBuilder().user(modifyUser(request.getUser())).build())
                .bidRequests(bidRequests)
                .bidderRequest(ContxtfulBidderRequest.of(BIDDER_NAME))
                .config(ContxtfulConfig.of(ContxtfulConfigDetails.of(DEFAULT_ADAPTER_VERSION, customerId)))
                .build();

        final HttpRequest<ContxtfulCompositeRequest> httpRequest = HttpRequest.<ContxtfulCompositeRequest>builder()
                .method(HttpMethod.POST)
                .uri(makeUrl(customerId))
                .headers(HttpUtil.headers())
                .body(mapper.encodeToBytes(outgoingRequest))
                .payload(outgoingRequest)
                .impIds(BidderUtil.impIds(request))
                .build();

        return Result.of(Collections.singletonList(httpRequest), errors);
    }

    private ExtImpContxtful parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Error parsing imp.ext for impression " + imp.getId());
        }
    }

    private static User modifyUser(User user) {
        if (user == null) {
            return null;
        }

        final String buyerUid = user.getBuyeruid();
        if (StringUtils.isNotBlank(buyerUid)) {
            return user;
        }

        return Optional.ofNullable(user.getExt())
                .map(ExtUser::getPrebid)
                .map(ExtUserPrebid::getBuyeruids)
                .map(buyerUids -> buyerUids.get(BIDDER_NAME))
                .filter(StringUtils::isNotBlank)
                .map(uid -> user.toBuilder().buyeruid(uid).build())
                .orElse(user);
    }

    private String makeUrl(String customerId) {
        return endpointUrl.replace(ACCOUNT_ID_MACRO, HttpUtil.encodeUrl(customerId));
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<ContxtfulCompositeRequest> httpCall, BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        try {
            final List<ContxtfulBid> responseBids = mapper.decodeValue(
                    httpCall.getResponse().getBody(),
                    new TypeReference<>() {
                    });
            return Result.of(extractBids(bidRequest, responseBids, errors), errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest,
                                               List<ContxtfulBid> responseBids,
                                               List<BidderError> errors) {

        if (CollectionUtils.isEmpty(responseBids)) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, responseBids, errors);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest,
                                                    List<ContxtfulBid> responseBids,
                                                    List<BidderError> errors) {

        final Map<String, Imp> impsMap = bidRequest.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, Function.identity()));

        return responseBids.stream()
                .filter(Objects::nonNull)
                .map(responseBid -> makeBidderBid(responseBid, impsMap, errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static BidderBid makeBidderBid(ContxtfulBid responseBid,
                                           Map<String, Imp> impsMap,
                                           List<BidderError> errors) {

        final String impId = responseBid.getRequestId();
        if (responseBid.getCpm() == null || impId == null) {
            return null;
        }

        if (StringUtils.isBlank(responseBid.getMediaType())) {
            errors.add(BidderError.badServerResponse("bid %s has no ad media type".formatted(impId)));
            return null;
        }

        if (StringUtils.isBlank(responseBid.getAdm())) {
            errors.add(BidderError.badServerResponse("bid %s has no ad markup".formatted(impId)));
            return null;
        }

        final Bid bid = Bid.builder()
                .id(BIDDER_NAME + "-" + impId)
                .impid(impId)
                .price(responseBid.getCpm())
                .adm(responseBid.getAdm())
                .w(responseBid.getWidth())
                .h(responseBid.getHeight())
                .crid(responseBid.getCreativeId())
                .nurl(responseBid.getNurl())
                .burl(responseBid.getBurl())
                .lurl(responseBid.getLurl())
                .ext(responseBid.getExt())
                .build();

        final String currency = Objects.toString(responseBid.getCurrency(), DEFAULT_CURRENCY);
        return BidderBid.of(bid, getBidType(impsMap.get(impId)), currency);
    }

    private static BidType getBidType(Imp imp) {
        if (imp == null) {
            return BidType.banner;
        }

        if (imp.getVideo() != null) {
            return BidType.video;
        } else if (imp.getXNative() != null) {
            return BidType.xNative;
        } else {
            return BidType.banner;
        }
    }
}

