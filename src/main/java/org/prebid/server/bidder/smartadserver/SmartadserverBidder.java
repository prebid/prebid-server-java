package org.prebid.server.bidder.smartadserver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

public class SmartadserverBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSmartadserver>> SMARTADSERVER_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final String secondaryEndpointUrl;
    private final JacksonMapper mapper;

    public SmartadserverBidder(String endpointUrl, String secondaryEndpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.secondaryEndpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(secondaryEndpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> modifiedImps = new ArrayList<>();
        final LinkedHashMap<Imp, ExtImpSmartadserver> impToExtImpMap = new LinkedHashMap<>();

        boolean isProgrammaticGuaranteed = false;

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpSmartadserver extImp = parseImpExt(imp);
                if (!isProgrammaticGuaranteed && extImp.isProgrammaticGuaranteed()) {
                    isProgrammaticGuaranteed = true;
                }
                impToExtImpMap.put(imp, extImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (impToExtImpMap.isEmpty()) {
            return Result.withErrors(errors);
        }

        final String extImpKey = isProgrammaticGuaranteed ? "smartadserver" : "bidder";
        impToExtImpMap.forEach((imp, extImp) -> modifiedImps.add(modifyImp(imp, extImp, extImpKey)));

        final ExtImpSmartadserver lastExtImp = impToExtImpMap.lastEntry().getValue();
        final BidRequest outgoingRequest = request.toBuilder()
                .imp(modifiedImps)
                .site(modifySite(request.getSite(), lastExtImp.getNetworkId()))
                .build();

        final HttpRequest<BidRequest> httpRequest = BidderUtil.defaultRequest(
                outgoingRequest,
                makeUrl(isProgrammaticGuaranteed),
                mapper);
        return Result.of(Collections.singletonList(httpRequest), errors);
    }

    private ExtImpSmartadserver parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), SMARTADSERVER_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Error parsing smartadserverExt parameters");
        }
    }

    private Imp modifyImp(Imp imp, ExtImpSmartadserver extImp, String impExtKey) {
        final ObjectNode impExt = imp.getExt().deepCopy();
        impExt.remove("bidder");
        impExt.set(impExtKey, mapper.mapper().valueToTree(extImp));
        return imp.toBuilder().ext(impExt).build();
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

    private String makeUrl(boolean isProgrammaticGuaranteed) {
        try {
            final URI uri = new URI(isProgrammaticGuaranteed ? secondaryEndpointUrl : endpointUrl);
            final String path = isProgrammaticGuaranteed ? "/ortb" : "/api/bid";
            final URIBuilder uriBuilder = new URIBuilder(uri)
                    .setPath(StringUtils.removeEnd(uri.getPath(), "/") + path);

            if (!isProgrammaticGuaranteed) {
                uriBuilder.addParameter("callerId", "5");
            }

            return uriBuilder.toString();
        } catch (URISyntaxException e) {
            throw new PreBidException("Malformed URL: %s.".formatted(secondaryEndpointUrl));
        }
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
