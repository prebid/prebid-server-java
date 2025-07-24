package org.prebid.server.bidder.rediads;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.prebid.server.proto.openrtb.ext.request.rediads.ExtImpRediads;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class RediadsBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpRediads>> TYPE_REFERENCE = new TypeReference<>() {
    };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public RediadsBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> modifiedImps = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        String accountId = null;
        String endpoint = null;

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpRediads extImp = parseImpExt(imp);
                modifiedImps.add(modifyImp(imp, extImp));
                accountId = extImp.getAccountId();
                endpoint = extImp.getEndpoint();
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (modifiedImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final BidRequest outgoingRequest = modifyRequest(request, modifiedImps, accountId);
        final String endpointUrl = resolveEndpointUrl(endpoint);
        final HttpRequest<BidRequest> httpRequest = BidderUtil.defaultRequest(outgoingRequest, endpointUrl, mapper);

        return Result.of(Collections.singletonList(httpRequest), errors);
    }

    private ExtImpRediads parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Invalid imp.ext for impression " + imp.getId());
        }
    }

    private Imp modifyImp(Imp imp, ExtImpRediads extImp) {
        final ObjectNode modifiedExt = imp.getExt().deepCopy();
        modifiedExt.remove("bidder");
        modifiedExt.remove("prebid");
        return imp.toBuilder()
                .tagid(StringUtils.defaultIfBlank(extImp.getSlot(), imp.getTagid()))
                .ext(modifiedExt)
                .build();
    }

    private BidRequest modifyRequest(BidRequest request, List<Imp> imps, String accountId) {
        final Site site = request.getSite();
        final App app = request.getApp();
        return request.toBuilder()
                .site(site != null ? modifySite(site, accountId) : null)
                .app(site == null && app != null ? modifyApp(app, accountId) : app)
                .imp(imps)
                .build();
    }

    private static Site modifySite(Site site, String accountId) {
        final Publisher originalPublisher = site.getPublisher();
        final Publisher newPublisher = originalPublisher != null
                ? originalPublisher.toBuilder().id(accountId).build()
                : Publisher.builder().id(accountId).build();
        return site.toBuilder().publisher(newPublisher).build();
    }

    private static App modifyApp(App app, String accountId) {
        final Publisher originalPublisher = app.getPublisher();
        final Publisher newPublisher = originalPublisher != null
                ? originalPublisher.toBuilder().id(accountId).build()
                : Publisher.builder().id(accountId).build();
        return app.toBuilder().publisher(newPublisher).build();
    }

    private String resolveEndpointUrl(String endpoint) {
        if (StringUtils.isBlank(endpoint)) {
            return endpointUrl;
        }

        try {
            final URL originalUrl = new URL(endpointUrl);
            final String originalHost = originalUrl.getHost();
            final String[] hostParts = originalHost.split("\\.");
            hostParts[0] = endpoint;
            final String newHost = String.join(".", hostParts);
            return endpointUrl.replace(originalHost, newHost);
        } catch (MalformedURLException e) {
            throw new PreBidException("Failed to parse endpoint URL: " + endpointUrl);
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidType(Bid bid) {
        return switch (bid.getMtype()) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 3 -> BidType.audio;
            case 4 -> BidType.xNative;
            case null, default -> throw new PreBidException(
                    "could not define media type for impression: " + bid.getImpid());
        };
    }
}
