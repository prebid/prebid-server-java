package org.prebid.server.bidder.adprime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
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
import org.prebid.server.proto.openrtb.ext.request.adprime.ExtImpAdprime;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AdprimeBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdprime>> ADPRIME_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AdprimeBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public final Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        final List<Imp> imps = bidRequest.getImp();

        for (Imp imp : imps) {
            try {
                final ExtImpAdprime extImpAdprime = mapper.mapper()
                        .convertValue(imp.getExt(), ADPRIME_EXT_TYPE_REFERENCE)
                        .getBidder();

                httpRequests.add(makeHttpRequest(
                        modifyBidRequest(bidRequest, extImpAdprime, modifyImp(imp, extImpAdprime))));
            } catch (IllegalArgumentException e) {
                return Result.withError(BidderError.badInput(
                        "Unable to decode the impression ext for id: '%s'".formatted(imp.getId())));
            }
        }

        return Result.of(httpRequests, Collections.emptyList());
    }

    private Imp modifyImp(Imp imp, ExtImpAdprime impExt) {
        final String tagId = impExt.getTagId();
        final ObjectNode modifiedImpExtBidder = mapper.mapper().createObjectNode();

        modifiedImpExtBidder.set("TagID", TextNode.valueOf(tagId));
        modifiedImpExtBidder.set("placementId", TextNode.valueOf(tagId));

        return imp.toBuilder()
                .tagid(tagId)
                .ext(mapper.mapper().createObjectNode().set("bidder", modifiedImpExtBidder))
                .build();
    }

    private BidRequest modifyBidRequest(BidRequest bidRequest, ExtImpAdprime extImpAdprime, Imp imp) {
        return bidRequest.toBuilder()
                .user(resolveUser(bidRequest, extImpAdprime))
                .site(resolveSite(bidRequest, extImpAdprime))
                .imp(List.of(imp))
                .build();
    }

    private User resolveUser(BidRequest bidRequest, ExtImpAdprime extImpAdprime) {
        final User user = bidRequest.getUser();
        final List<String> audiences = extImpAdprime.getAudiences();

        if (Objects.isNull(bidRequest.getSite()) || CollectionUtils.isEmpty(audiences)) {
            return user;
        }

        final User.UserBuilder userBuilder = user != null ? user.toBuilder() : User.builder();

        return userBuilder.customdata(String.join(",", audiences)).build();
    }

    private Site resolveSite(BidRequest bidRequest, ExtImpAdprime extImpAdprime) {
        final Site site = bidRequest.getSite();
        final List<String> keywords = extImpAdprime.getKeywords();

        return Objects.nonNull(site) && CollectionUtils.isNotEmpty(keywords)
                ? site.toBuilder()
                .keywords(String.join(",", keywords))
                .build()
                : site;
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest bidRequest) {
        return BidderUtil.defaultRequest(bidRequest, endpointUrl, mapper);
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> errors = new ArrayList<>();
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, errors);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<BidderError> errors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> resolveBidderBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid resolveBidderBid(Bid bid, String currency, List<BidderError> errors) {
        final BidType bidType;
        try {
            bidType = getBidType(bid);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
        return BidderBid.of(bid, bidType, currency);
    }

    private static BidType getBidType(Bid bid) {
        final Integer markupType = bid.getMtype();
        if (markupType == null) {
            throw new PreBidException("Missing MType for bid: " + bid.getId());
        }

        return switch (markupType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 4 -> BidType.xNative;
            default -> throw new PreBidException(
                    "Unable to fetch mediaType " + bid.getMtype() + " in multi-format: " + bid.getImpid());
        };
    }

}
