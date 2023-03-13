package org.prebid.server.bidder.taboola;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.taboola.ExtImpTaboola;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class TaboolaBidder implements Bidder<BidRequest> {

    private static final String DISPLAY_ENDPOINT_PREFIX = "display";

    private static final TypeReference<ExtPrebid<?, ExtImpTaboola>> TABOOLA_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointTemplate;
    private final String domain;
    private final JacksonMapper mapper;

    public TaboolaBidder(String endpointTemplate, String externalUrl, JacksonMapper mapper) {
        this.endpointTemplate = HttpUtil.validateUrl(Objects.requireNonNull(endpointTemplate));
        this.domain = HttpUtil.getHostFromUrl(externalUrl);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            ExtImpTaboola impExt;
            try {
                validateImp(imp);
                impExt = parseImpExt(imp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }
            String type = getBidType(imp).equals(BidType.banner)
                    ? DISPLAY_ENDPOINT_PREFIX
                    : BidType.xNative.getName();

            final BidRequest outgoingRequest = createRequest(request, imp, impExt);
            HttpRequest<BidRequest> httpRequest = createHttpRequest(impExt, type, outgoingRequest);
            httpRequests.add(httpRequest);
        }
        return Result.of(httpRequests, errors);
    }

    private HttpRequest<BidRequest> createHttpRequest(ExtImpTaboola impExt, String type, BidRequest outgoingRequest) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(buildEndpointUrl(impExt, type, domain))
                .body(mapper.encodeToBytes(outgoingRequest))
                .headers(HttpUtil.headers())
                .payload(outgoingRequest)
                .build();
    }

    private static void validateImp(Imp imp) {
        if (imp.getBanner() == null && imp.getXNative() == null) {
            throw new PreBidException("For Imp ID %s Banner or Native is undefined".formatted(imp.getId()));
        }
    }

    private ExtImpTaboola parseImpExt(Imp imp) throws PreBidException {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TABOOLA_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Missing bidder ext in impression with id: " + imp.getId());
        }
    }

    private BidRequest createRequest(BidRequest request, Imp taboolaImp, ExtImpTaboola impExt) {
        Imp updatedImp = modifyImp(taboolaImp, impExt);
        final List<String> blockedAdomain = impExt.getBAdv();
        final List<String> blockedAdvCat = impExt.getBCat();
        final ExtRequest initialExt = (request.getExt() == null) ? ExtRequest.empty() : request.getExt();
        final ExtRequest modifiedExtRequest = StringUtils.isNotEmpty(impExt.getPageType())
                ? modifyExtRequest(initialExt, impExt.getPageType())
                : initialExt;

        Site newSite = Optional.ofNullable(request.getSite())
                .map(Site::toBuilder)
                .orElseGet(Site::builder)
                .id(impExt.getPublisherId())
                .name(impExt.getPublisherId())
                .domain(StringUtils.defaultString(impExt.getPublisherDomain()))
                .publisher(Publisher.builder().id(impExt.getPublisherId()).build())
                .build();

        return request.toBuilder()
                .badv(CollectionUtils.isNotEmpty(blockedAdomain) ? blockedAdomain : request.getBadv())
                .bcat(CollectionUtils.isNotEmpty(blockedAdvCat) ? blockedAdvCat : request.getBcat())
                .imp(Collections.singletonList(updatedImp))
                .ext(modifiedExtRequest)
                .site(newSite)
                .build();
    }

    private String buildEndpointUrl(ExtImpTaboola extImpTaboola, String type, String domain) {
        return endpointTemplate.replace("{{Host}}", domain)
                .replace("{{MediaType}}", type)
                .replace("{{PublisherID}}", extImpTaboola.getPublisherId());
    }

    private static Imp modifyImp(Imp imp, ExtImpTaboola impExt) {
        Banner banner = imp.getBanner();
        if (banner != null && impExt.getPosition() != null) {
            banner = banner.toBuilder().pos(impExt.getPosition()).build();
        }
        return imp.toBuilder()
                .tagid(impExt.getPublisherId())
                .bidfloor(impExt.getBidFloor())
                .banner(banner)
                .build();
    }

    private ExtRequest modifyExtRequest(ExtRequest extRequest, String pageType) {
        final ObjectNode adfNode = mapper.mapper().createObjectNode().put("pageType", pageType);
        return mapper.fillExtension(extRequest, adfNode);
    }

    private static BidType getBidType(Imp imp) {
        return imp.getBanner() != null ? BidType.banner : BidType.xNative;
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
        final List<BidderError> errors = new ArrayList<>();
        return bidsFromResponse(bidRequest, bidResponse, errors);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest,
                                             BidResponse bidResponse,
                                             List<BidderError> errors) {

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> resolveBidderBid(bidResponse.getCur(), bidRequest.getImp(), bid, errors))
                .toList();
    }

    private BidderBid resolveBidderBid(String currency, List<Imp> imps, Bid bid, List<BidderError> errors) {
        final BidType bidType;
        try {
            bidType = resolveBidType(bid.getImpid(), imps);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
        return BidderBid.of(bid, bidType, currency);
    }

    private BidType resolveBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
                return BidType.banner;
            }
        }
        throw new PreBidException("Failed to find impression \"%s\"".formatted(impId));
    }
}
