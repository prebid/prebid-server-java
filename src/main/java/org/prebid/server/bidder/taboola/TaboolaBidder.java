package org.prebid.server.bidder.taboola;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
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
import org.prebid.server.proto.openrtb.ext.request.taboola.ExtImpTaboola;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class TaboolaBidder implements Bidder<BidRequest> {

    private static final String DISPLAY_ENDPOINT_PREFIX = "display";

    private static final TypeReference<ExtPrebid<?, ExtImpTaboola>> TABOOLA_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointTemplate;
    private final JacksonMapper mapper;
    private final MultiMap headers;

    public TaboolaBidder(String endpointTemplate, JacksonMapper mapper) {
        this.endpointTemplate = HttpUtil.validateUrl(Objects.requireNonNull(endpointTemplate));
        this.mapper = Objects.requireNonNull(mapper);
        headers = HttpUtil.headers();
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                validateImp(imp);

                final ExtImpTaboola impExt = parseImpExt(imp);
                final String type = getBidType(imp).equals(BidType.banner)
                        ? DISPLAY_ENDPOINT_PREFIX
                        : BidType.xNative.getName();

                final BidRequest outgoingRequest = createRequest(request, imp, impExt);

                httpRequests.add(HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(buildEndpointUrl(impExt, type, outgoingRequest.getSite().getDomain()))
                        .body(mapper.encodeToBytes(outgoingRequest))
                        .headers(headers)
                        .payload(outgoingRequest)
                        .build());
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        return Result.of(httpRequests, errors);
    }

    private BidRequest createRequest(BidRequest request, Imp taboolaImp, ExtImpTaboola impExt) {
        Imp updatedImp = modifyImp(taboolaImp, impExt);
        final List<String> blockedAdomain = impExt.getBAdv();
        final List<String> blockedAdvCat = impExt.getBCat();
        Site newSite;
        if (request.getSite() == null) {
            newSite = Site.builder()
                    .id(impExt.getPublisherId())
                    .name(impExt.getPublisherId())
                    .domain(StringUtils.defaultString(impExt.getPublisherDomain()))
                    .publisher(Publisher.builder().id(impExt.getPublisherId()).build())
                    .build();
        } else {
            newSite = request.getSite();
        }
        return request.toBuilder()
                .badv(CollectionUtils.isNotEmpty(blockedAdomain) ? blockedAdomain : request.getBadv())
                .bcat(CollectionUtils.isNotEmpty(blockedAdvCat) ? blockedAdvCat : request.getBcat())
                .imp(Collections.singletonList(updatedImp))
                .site(newSite)
                .build();
    }

    private ExtImpTaboola parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TABOOLA_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Missing bidder ext in impression with id: " + imp.getId());
        }
    }

    private String buildEndpointUrl(ExtImpTaboola extImpTaboola, String type, String domain) {
        return endpointTemplate.replace("{{Host}}", domain)
                .replace("{{MediaType}}", type)
                .replace("{{PublisherID}}", extImpTaboola.getPublisherId());
    }

    private static void validateImp(Imp imp) {
        if (imp.getBanner() == null && imp.getXNative() == null) {
            throw new PreBidException("For Imp ID %s Banner or Native is undefined".formatted(imp.getId()));
        }
    }

    private static Imp modifyImp(Imp imp, ExtImpTaboola impExt) {
        return imp.toBuilder()
                .tagid(impExt.getPublisherId())
                .bidfloor(impExt.getBidFloor())
                .build();
    }

    private static BidType getBidType(Imp imp) {
        if (imp.getBanner() != null) {
            return BidType.banner;
        } else if (imp.getXNative() != null) {
            return BidType.xNative;
        } else {
            throw new PreBidException("Failed to find impression for ID: " + imp.getId());
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest,
                                             BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bidRequest.getImp().get(0)), bidResponse.getCur()))
                .toList();
    }
}
