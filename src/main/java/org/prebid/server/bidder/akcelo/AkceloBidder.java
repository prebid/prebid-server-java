package org.prebid.server.bidder.akcelo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisherPrebid;
import org.prebid.server.proto.openrtb.ext.request.akcelo.ExtImpAkcelo;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class AkceloBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAkcelo>> AKCELO_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String BIDDER_NAME = "akcelo";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AkceloBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> modifiedImps = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        String siteId = null;

        try {
            final List<Imp> imps = request.getImp();
            for (int i = 0; i < imps.size(); i++) {
                final Imp imp = imps.get(i);
                if (i == 0) {
                    final ExtImpAkcelo extImp = parseImpExt(imp);
                    siteId = extImp.getSiteId();
                }
                modifiedImps.add(modifyImp(imp));
            }
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
        }

        if (modifiedImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final BidRequest outgoingRequest = modifyRequest(request, modifiedImps, siteId);
        final HttpRequest<BidRequest> httpRequest = BidderUtil.defaultRequest(outgoingRequest, endpointUrl, mapper);
        return Result.of(Collections.singletonList(httpRequest), errors);
    }

    private ExtImpAkcelo parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), AKCELO_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Imp modifyImp(Imp imp) {
        return Optional.ofNullable(imp.getExt())
                .map(prebid -> prebid.get("bidder"))
                .filter(JsonNode::isObject)
                .map(bidder -> (ObjectNode) mapper.mapper().createObjectNode().set(BIDDER_NAME, bidder))
                .map(ext -> imp.toBuilder().ext(ext).build())
                .orElseThrow(() -> new PreBidException("imp.ext.prebid.bidder can't be parsed"));
    }

    private BidRequest modifyRequest(BidRequest request, List<Imp> imps, String siteId) {
        return request.toBuilder()
                .imp(imps)
                .site(modifySite(request.getSite(), siteId))
                .build();
    }

    private Site modifySite(Site site, String siteId) {
        final Publisher publisher = Optional.ofNullable(site)
                .map(Site::getPublisher)
                .map(Publisher::toBuilder)
                .orElseGet(Publisher::builder)
                .ext(ExtPublisher.of(ExtPublisherPrebid.of(siteId)))
                .build();

        return Optional.ofNullable(site)
                .map(Site::toBuilder)
                .orElseGet(Site::builder)
                .publisher(publisher)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid makeBid(Bid bid, String currency, List<BidderError> errors) {
        final BidType bidType = getBidType(bid, errors);
        return bidType == null ? null : BidderBid.of(bid, bidType, currency);
    }

    private BidType getBidType(Bid bid, List<BidderError> errors) {
        final Integer mType = bid.getMtype();
        if (mType != null) {
            return switch (mType) {
                case 1 -> BidType.banner;
                case 2 -> BidType.video;
                case 4 -> BidType.xNative;
                default -> {
                    errors.add(BidderError.badServerResponse("unable to get media type " + mType));
                    yield null;
                }
            };
        }

        return getExtBidPrebidType(bid, errors);
    }

    private BidType getExtBidPrebidType(Bid bid, List<BidderError> errors) {
        return Optional.ofNullable(bid.getExt())
                .map(ext -> ext.get("prebid"))
                .filter(JsonNode::isObject)
                .map(ObjectNode.class::cast)
                .map(this::parseExtBidPrebid)
                .map(ExtBidPrebid::getType)
                .orElseGet(() -> {
                    errors.add(BidderError.badServerResponse("missing media type for bid " + bid.getId()));
                    return null;
                });
    }

    private ExtBidPrebid parseExtBidPrebid(ObjectNode prebid) {
        try {
            return mapper.mapper().treeToValue(prebid, ExtBidPrebid.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
