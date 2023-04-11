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
import org.prebid.server.model.UpdateResult;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidServer;
import org.prebid.server.proto.openrtb.ext.request.taboola.ExtImpTaboola;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.spring.config.bidder.model.MediaType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class TaboolaBidder implements Bidder<BidRequest> {

    private static final String DISPLAY_ENDPOINT_PREFIX = "display";
    private static final String NATIVE_ENDPOINT_PREFIX = "native";
    private static final String PRICE_MACRO = "${AUCTION_PRICE}";

    private static final TypeReference<ExtPrebid<?, ExtImpTaboola>> TABOOLA_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointTemplate;
    private final JacksonMapper mapper;

    public TaboolaBidder(String endpointTemplate, JacksonMapper mapper) {
        this.endpointTemplate = HttpUtil.validateUrl(Objects.requireNonNull(endpointTemplate));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final Map<MediaType, List<Imp>> mediaTypeToImps = new HashMap<>();
        ExtImpTaboola extImpTaboola = null;

        for (Imp imp : request.getImp()) {
            try {
                extImpTaboola = parseImpExt(imp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }

            final MediaType impMediaType = getMediaType(imp);
            if (impMediaType == null) {
                continue;
            }

            final Imp modifiedImp = modifyImp(imp, extImpTaboola);
            mediaTypeToImps
                    .computeIfAbsent(impMediaType, key -> new ArrayList<>())
                    .add(modifiedImp);
        }

        if (!errors.isEmpty()) {
            return Result.withErrors(errors);
        }

        final String gvlId = extractGvlId(request);

        final ExtImpTaboola lastExtImp = extImpTaboola != null ? extImpTaboola : ExtImpTaboola.empty();
        final List<HttpRequest<BidRequest>> httpRequests = mediaTypeToImps.entrySet().stream()
                .map(entry -> createHttpRequest(
                        entry.getKey(),
                        createRequest(request, entry.getValue(), lastExtImp),
                        gvlId))
                .toList();

        return Result.withValues(httpRequests);
    }

    private static MediaType getMediaType(Imp imp) {
        if (imp.getBanner() != null) {
            return MediaType.BANNER;
        } else if (imp.getXNative() != null) {
            return MediaType.NATIVE;
        }

        return null;
    }

    private ExtImpTaboola parseImpExt(Imp imp) throws PreBidException {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TABOOLA_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private static Imp modifyImp(Imp imp, ExtImpTaboola impExt) {
        final String impExtTagId = impExt.getTagId();
        final UpdateResult<String> resolvedTagId = StringUtils.length(impExtTagId) < 1
                ? UpdateResult.updated(impExt.getLowerCaseTagId())
                : UpdateResult.updated(impExtTagId);

        final BigDecimal impExtBidFloor = impExt.getBidFloor();
        final UpdateResult<BigDecimal> resolvedBidFloor = BidderUtil.isValidPrice(impExtBidFloor)
                ? UpdateResult.updated(impExtBidFloor)
                : UpdateResult.unaltered(imp.getBidfloor());

        final Banner impBanner = imp.getBanner();
        final Integer impExtPos = impExt.getPosition();
        final UpdateResult<Banner> resolvedBanner = impBanner != null && impExtPos != null
                ? UpdateResult.updated(impBanner.toBuilder().pos(impExtPos).build())
                : UpdateResult.unaltered(impBanner);

        return resolvedTagId.isUpdated() || resolvedBidFloor.isUpdated() || resolvedBanner.isUpdated()

                ? imp.toBuilder()
                .tagid(resolvedTagId.getValue())
                .bidfloor(resolvedBidFloor.getValue())
                .banner(resolvedBanner.getValue())
                .build()

                : imp;
    }

    private BidRequest createRequest(BidRequest request, List<Imp> imps, ExtImpTaboola impExt) {
        final String impExtPublisherId = StringUtils.defaultString(impExt.getPublisherId());
        final List<String> impExtBAdv = impExt.getBAdv();
        final List<String> impExtBCat = impExt.getBCat();
        final String impExtPageType = impExt.getPageType();

        final Site site = Optional.ofNullable(request.getSite())
                .map(Site::toBuilder)
                .orElseGet(Site::builder)
                .id(impExtPublisherId)
                .name(impExtPublisherId)
                .domain(resolveDomain(impExt.getPublisherDomain(), request))
                .publisher(Publisher.builder().id(impExtPublisherId).build())
                .build();

        final ExtRequest extRequest = StringUtils.isNotEmpty(impExtPageType)
                ? createExtRequest(impExtPageType)
                : request.getExt();

        return request.toBuilder()
                .imp(imps)
                .site(site)
                .badv(CollectionUtils.isNotEmpty(impExtBAdv) ? impExtBAdv : request.getBadv())
                .bcat(CollectionUtils.isNotEmpty(impExtBCat) ? impExtBCat : request.getBcat())
                .ext(extRequest)
                .build();
    }

    private String resolveDomain(String impExtPublisherDomain, BidRequest request) {
        return StringUtils.isNotEmpty(impExtPublisherDomain)
                ? impExtPublisherDomain
                : Optional.ofNullable(request.getSite())
                .map(Site::getDomain)
                .orElse(StringUtils.EMPTY);
    }

    private ExtRequest createExtRequest(String pageType) {
        final ExtRequest extRequest = ExtRequest.empty();
        final ObjectNode objectNode = mapper.mapper().createObjectNode().put("pageType", pageType);
        return mapper.fillExtension(extRequest, objectNode);
    }

    private HttpRequest<BidRequest> createHttpRequest(MediaType type, BidRequest outgoingRequest, String gvlId) {
        return BidderUtil.defaultRequest(outgoingRequest,
                buildEndpointUrl(outgoingRequest.getSite().getId(), type, gvlId),
                mapper);
    }

    private String buildEndpointUrl(String publisherId, MediaType mediaType, String gvlId) {
        final String type = switch (mediaType) {
            case BANNER -> DISPLAY_ENDPOINT_PREFIX;
            case NATIVE -> NATIVE_ENDPOINT_PREFIX;

            // should never happen
            default -> throw new AssertionError();
        };

        return endpointTemplate
                .replace("{{GvlID}}", gvlId)
                .replace("{{MediaType}}", type)
                .replace("{{PublisherID}}", HttpUtil.encodeUrl(publisherId));
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;
        try {
            bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bids = extractBids(httpCall.getRequest().getPayload(), bidResponse, errors);

        return Result.of(bids, errors);
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
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
                .filter(Objects::nonNull)
                .map(bid -> resolveBidderBid(bidResponse.getCur(), bidRequest.getImp(), bid, errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid resolveBidderBid(String currency, List<Imp> imps, Bid bid, List<BidderError> errors) {
        try {
            return BidderBid.of(resolveMacros(bid), resolveBidType(bid.getImpid(), imps), currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private BidType resolveBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
            }
        }
        throw new PreBidException("Failed to find banner/native impression \"%s\"".formatted(impId));
    }

    private static Bid resolveMacros(Bid bid) {
        final BigDecimal price = bid.getPrice();
        final String priceAsString = price != null ? price.toPlainString() : "0";

        return bid.toBuilder()
                .nurl(StringUtils.replace(bid.getNurl(), PRICE_MACRO, priceAsString))
                .adm(StringUtils.replace(bid.getAdm(), PRICE_MACRO, priceAsString))
                .build();
    }

    private static String extractGvlId(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest)
                .map(BidRequest::getExt)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getServer)
                .map(ExtRequestPrebidServer::getGvlId)
                .map(Object::toString)
                .orElse("");
    }
}
