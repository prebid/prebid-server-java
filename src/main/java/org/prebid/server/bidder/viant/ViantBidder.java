package org.prebid.server.bidder.viant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.App;
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
import org.prebid.server.proto.openrtb.ext.request.viant.ExtImpViant;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ViantBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpViant>> TYPE_REFERENCE = new TypeReference<>() {
    };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ViantBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = mapper;
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        String publisherId = null;

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpViant extImp = parseImpExt(imp);
                publisherId = extImp.getPublisherId();
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
        }

        final BidRequest outgoingRequest = modifyRequest(request, publisherId);
        return Result.withValue(BidderUtil.defaultRequest(outgoingRequest, endpointUrl, mapper));
    }

    private ExtImpViant parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Invalid imp.ext for impression %s. Error Information: %s"
                    .formatted(imp.getId(), e.getMessage()));
        }
    }

    private BidRequest modifyRequest(BidRequest request, String publisherId) {
        final Site site = request.getSite();
        final App app = request.getApp();

        return request.toBuilder()
                .site(site != null ? modifySite(site, publisherId) : null)
                .app(app != null ? modifyApp(app, publisherId) : null)
                .build();
    }

    private static Site modifySite(Site site, String publisherId) {
        return site.toBuilder().publisher(modifyPublisher(site.getPublisher(), publisherId)).build();
    }

    private static App modifyApp(App app, String publisherId) {
        return app.toBuilder().publisher(modifyPublisher(app.getPublisher(), publisherId)).build();
    }

    private static Publisher modifyPublisher(Publisher publisher, String publisherId) {
        return Optional.ofNullable(publisher)
                .map(Publisher::toBuilder)
                .orElseGet(Publisher::builder)
                .id(publisherId)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, errors);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<BidderError> errors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid).filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid makeBidderBid(Bid bid, String currency, List<BidderError> errors) {
        final BidType bidType = getBidMediaType(bid, errors);
        return bidType != null
                ? BidderBid.of(bid, bidType, currency)
                : null;
    }

    private static BidType getBidMediaType(Bid bid, List<BidderError> errors) {
        final Integer mType = bid.getMtype();
        return switch (mType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 3 -> BidType.audio;
            case 4 -> BidType.xNative;
            case null, default -> {
                errors.add(BidderError.badServerResponse(
                        String.format("Unsupported MType %d for bid %s", mType, bid.getId())));
                yield null;
            }
        };
    }
}
