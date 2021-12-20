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
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adprime.ExtImpAdprime;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
                        String.format("Unable to decode the impression ext for id: '%s'", imp.getId())));
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
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(HttpUtil.headers())
                .payload(bidRequest)
                .body(mapper.encodeToBytes(bidRequest))
                .build();
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> errors = new ArrayList<>();
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse,
                                               List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse, errors);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse,
                                                    List<BidderError> errors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> resolveBidderBid(bid, bidResponse.getCur(), bidRequest.getImp(), errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static BidderBid resolveBidderBid(Bid bid, String currency, List<Imp> imps, List<BidderError> errors) {
        final BidType bidType;
        try {
            bidType = getBidType(bid.getImpid(), imps);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
        return BidderBid.of(bid, bidType, currency);
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                }
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
                if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
                throw new PreBidException(String.format("Unknown impression type for ID: '%s'", impId));
            }
        }
        throw new PreBidException(String.format("Failed to find impression for ID: '%s'", impId));
    }

}
