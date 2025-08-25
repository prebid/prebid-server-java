package org.prebid.server.bidder.smartadserver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
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
import org.prebid.server.proto.openrtb.ext.request.smartadserver.ExtImpSmartadserver;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SmartadserverBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSmartadserver>> SMARTADSERVER_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SmartadserverBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> imps = new ArrayList<>();
        ExtImpSmartadserver extImp = null;

        for (Imp imp : request.getImp()) {
            try {
                extImp = parseImpExt(imp);
                imps.add(imp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (imps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final BidRequest outgoingRequest = request.toBuilder()
                .imp(imps)
                .site(modifySite(request.getSite(), extImp.getNetworkId()))
                .build();

        final HttpRequest<BidRequest> httpRequest = BidderUtil.defaultRequest(outgoingRequest, makeUrl(), mapper);
        return Result.of(Collections.singletonList(httpRequest), errors);
    }

    private ExtImpSmartadserver parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), SMARTADSERVER_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Error parsing smartadserverExt parameters");
        }
    }

    private static Site modifySite(Site site, Integer networkId) {
        final Site.SiteBuilder siteBuilder = site != null ? site.toBuilder() : Site.builder();
        final Publisher sitePublisher = site != null ? site.getPublisher() : null;

        return siteBuilder.publisher(modifyPublisher(sitePublisher, networkId)).build();
    }

    private static Publisher modifyPublisher(Publisher publisher, Integer networkId) {
        final Publisher.PublisherBuilder publisherBuilder = publisher != null
                ? publisher.toBuilder()
                : Publisher.builder();

        return publisherBuilder.id(String.valueOf(networkId)).build();
    }

    private String makeUrl() {
        final URI uri;
        try {
            uri = new URI(endpointUrl);
        } catch (URISyntaxException e) {
            throw new PreBidException("Malformed URL: %s.".formatted(endpointUrl));
        }
        return new URIBuilder(uri)
                .setPath(StringUtils.removeEnd(uri.getPath(), "/") + "/api/bid")
                .addParameter("callerId", "5")
                .toString();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return extractBids(bidResponse);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private Result<List<BidderBid>> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Result.empty();
        }
        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bidderBids = bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidTypeFromMarkupType(bid.getMtype()), bidResponse.getCur()))
                .toList();
        return Result.of(bidderBids, errors);
    }

    private static BidType getBidTypeFromMarkupType(Integer markupType) {
        return switch (markupType) {
            case 2 -> BidType.video;
            case 3 -> BidType.audio;
            case 4 -> BidType.xNative;
            case null, default -> BidType.banner;
        };
    }
}
