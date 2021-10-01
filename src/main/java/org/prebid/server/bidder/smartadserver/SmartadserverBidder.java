package org.prebid.server.bidder.smartadserver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
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
import org.prebid.server.proto.openrtb.ext.request.smartadserver.ExtImpSmartadserver;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SmartadserverBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSmartadserver>> SMARTADSERVER_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpSmartadserver>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SmartadserverBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> result = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpSmartadserver extImpSmartadserver = parseImpExt(imp);
                final BidRequest updatedRequest = request.toBuilder()
                        .imp(Collections.singletonList(imp))
                        .site(modifySite(request.getSite(), extImpSmartadserver.getNetworkId()))
                        .build();
                result.add(createSingleRequest(updatedRequest));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        return Result.of(result, errors);
    }

    private ExtImpSmartadserver parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), SMARTADSERVER_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Error parsing smartadserverExt parameters");
        }
    }

    private HttpRequest<BidRequest> createSingleRequest(BidRequest request) {

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(getUri())
                .headers(HttpUtil.headers())
                .body(mapper.encode(request))
                .payload(request)
                .build();
    }

    private String getUri() {
        final URI uri;
        try {
            uri = new URI(endpointUrl);
        } catch (URISyntaxException e) {
            throw new PreBidException(String.format("Malformed URL: %s.", endpointUrl));
        }
        return new URIBuilder(uri)
                .setPath(String.format("%s/api/bid", StringUtils.removeEnd(uri.getPath(), "/")))
                .addParameter("callerId", "5")
                .toString();
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

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return extractBids(httpCall.getRequest().getPayload(), bidResponse);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private Result<List<BidderBid>> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Result.empty();
        }
        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bidderBids = bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .collect(Collectors.toList());
        return Result.of(bidderBids, errors);
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                return imp.getVideo() != null ? BidType.video : BidType.banner;
            }
        }
        return BidType.banner;
    }
}
