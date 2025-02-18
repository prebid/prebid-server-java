package org.prebid.server.bidder.connatix;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.connatix.proto.ConnatixImpExtBidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtAppPrebid;
import org.prebid.server.proto.openrtb.ext.request.connatix.ExtImpConnatix;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ConnatixBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpConnatix>> CONNATIX_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final int MAX_IMPS_PER_REQUEST = 1;

    private final String endpointUrl;
    private final JacksonMapper mapper;

    //    private static final String PREBID_KEY = "prebid";
    //    private static final String SOURCE_PROPERTY = "source";
    //    private static final String VERSION_PROPERTY = "version";
    private static final String BIDDER_CURRENCY = "USD";

    private final CurrencyConversionService currencyConversionService;

    public ConnatixBidder(String endpointUrl,
                          CurrencyConversionService currencyConversionService,
                          JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.currencyConversionService = currencyConversionService;
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        // Device IP required - bounce if not available
        if (request.getDevice() == null
                || (request.getDevice().getIp() == null && request.getDevice().getIpv6() == null)) {
            return Result.withError(BidderError.badInput("Device IP is required"));
        }

        // KATIE TO DO. UPDATE THIS LOGIC TO PAY ATTENTION TO display manager.
        // display manager version can come from openrtb2 request OR imp.ext.prebid
        // KIM: i updated the logic to do the same as Appnexus which seems to match? maybe
        final String displayManagerVer = buildDisplayManagerVersion(request);

        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            final ExtImpConnatix extImpConnatix;
            try {
                extImpConnatix = parseExtImp(imp);
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
            // KATIE to do - probably need to add logic for splitting requests somewhere in here
            final Price bidFloorPrice = convertBidFloor(imp, request);

            final Imp modifiedImp = modifyImp(imp, extImpConnatix, displayManagerVer, bidFloorPrice);
            httpRequests.add(makeHttpRequest(request, modifiedImp));
        }

        return Result.withValues(httpRequests);

    }

    private Imp modifyImp(Imp imp, ExtImpConnatix extImpConnatix, String displayManagerVer, Price bidFloorPrice) {
        //KATIE to do - fix this method, it isn't right :)
        // KIM: added these thingies to the method - modified banner, display manager, modified bid floor and currency
        final ConnatixImpExtBidder impExtBidder = resolveImpExt(extImpConnatix);

        final ObjectNode impExtBidderNode = mapper.mapper().valueToTree(impExtBidder);

        final ObjectNode modifiedImpExtBidder = imp.getExt() != null ? imp.getExt().deepCopy()
                : mapper.mapper().createObjectNode();

        modifiedImpExtBidder.setAll(impExtBidderNode);

        return imp.toBuilder()
                .ext(modifiedImpExtBidder)
                .banner(modifyImpBanner(imp.getBanner()))
                .displaymanagerver(displayManagerVer)
                .bidfloor(bidFloorPrice.getValue())
                .bidfloorcur(bidFloorPrice.getCurrency())
                .build();
    }

    private Banner modifyImpBanner(Banner banner) {
        if (banner == null) {
            return null;
        }

        if (banner.getW() == null && banner.getH() == null && !CollectionUtils.isEmpty(banner.getFormat())) {
            final Format firstFormat = banner.getFormat().getFirst();
            return banner.toBuilder()
                    .w(firstFormat.getW())
                    .h(firstFormat.getH())
                    .build();
        }
        return banner;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            // KATIE check validity of this logic for setting currency to usd. explicitly set to USD in go version
            final BidResponse updatedResponse = bidResponse.toBuilder().cur("USD").build();
            final List<BidderBid> bids = extractBids(httpCall.getRequest().getPayload(), updatedResponse);
            return Result.withValues(bids);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private Price convertBidFloor(Imp imp, BidRequest bidRequest) {
        final Price initialBidFloorPrice = Price.of(imp.getBidfloorcur(), imp.getBidfloor());
        if (BidderUtil.isValidPrice(initialBidFloorPrice)) {
            try {
                final BigDecimal convertedPrice = currencyConversionService
                        .convertCurrency(imp.getBidfloor(), bidRequest, imp.getBidfloorcur(), BIDDER_CURRENCY);
                return Price.of(BIDDER_CURRENCY, convertedPrice);
            } catch (PreBidException e) {
                throw new PreBidException("Unable to convert provided bid floor currency from %s to %s for imp `%s`"
                        .formatted(imp.getBidfloorcur(), BIDDER_CURRENCY, imp.getId()));
            }
        }
        return initialBidFloorPrice;
    }

    // extract bids
    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .toList();
    }

    // parseExtImp
    private ExtImpConnatix parseExtImp(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), CONNATIX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private ConnatixImpExtBidder resolveImpExt(ExtImpConnatix extImpConnatix) {

        final ConnatixImpExtBidder.ConnatixImpExtBidderBuilder builder = ConnatixImpExtBidder.builder();
        // KATIE check this is correct - adding placement ID and viewability percentage if available
        if (StringUtils.isNotEmpty(extImpConnatix.getPlacementId())) {
            builder.placementId(extImpConnatix.getPlacementId());
        }
        if (extImpConnatix.getViewabilityPercentage() != null) {
            builder.viewabilityPercentage(extImpConnatix.getViewabilityPercentage());
        }

        return builder.build();
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request, Imp imp) {
        final BidRequest outgoingRequest = request.toBuilder().imp(List.of(imp)).build();

        return BidderUtil.defaultRequest(outgoingRequest, endpointUrl, mapper);
    }

    private HttpRequest<BidRequest> createHttpRequest(BidRequest bidRequest,
                                                      List<Imp> imps,
                                                      String url,
                                                      MultiMap headers) {
        return BidderUtil.defaultRequest(bidRequest.toBuilder().imp(imps).build(), headers, url, mapper);
    }

    private List<HttpRequest<BidRequest>> splitHttpRequests(BidRequest bidRequest,
                                                            List<Imp> imps,
                                                            String url) {
        final MultiMap httpHeaders = resolveHeaders(bidRequest.getDevice());
        // KATIE TO DO: this is how we've done it elsewhere but go version has logic that
        // explicitly modifies headers here - need to figure out what that's about
        // KIM: added the headers - seems like the same logic as AcuityadsBidder
        return ListUtils.partition(imps, MAX_IMPS_PER_REQUEST)
                .stream()
                .map(impsChunk -> createHttpRequest(bidRequest, impsChunk, url, httpHeaders))
                .toList();
    }

    private MultiMap resolveHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers();
        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIpv6());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
        }
        return headers;
    }

    // check display manager version
    private String buildDisplayManagerVersion(BidRequest request) {
        // KIM: copied this from the AppnexusBidder - i think this is what ExtAppPrebid is for
        final Optional<ExtAppPrebid> prebid = Optional.ofNullable(request.getApp())
                .map(App::getExt)
                .map(ExtApp::getPrebid);

        final String source = prebid.map(ExtAppPrebid::getSource).orElse(null);
        final String version = prebid.map(ExtAppPrebid::getVersion).orElse(null);

        return ObjectUtils.allNotNull(source, version)
                ? "%s-%s".formatted(source, version)
                : "";
//        if (request.getApp() == null || request.getApp().getExt() == null) {
//            return "";
//        }
//
//        try {
//            final JsonNode extNode = mapper.mapper().readTree(String.valueOf(request.getApp().getExt()));
//            final JsonNode prebidNode = extNode.path(PREBID_KEY);
//
//            final String source = prebidNode.path(SOURCE_PROPERTY).asText("");
//            final String version = prebidNode.path(VERSION_PROPERTY).asText("");
//
//            return (StringUtils.isNotEmpty(source) && StringUtils.isNotEmpty(version))
//                    ? source + "-" + version
//                    : "";
//
//        } catch (Exception e) {
//            return "";
//        }
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                // KATIE TO DO - this is how go version gets mediaType - validate this
                final String mediaType = imp.getExt().get("cnx").get("mediaType").asText();
                if (mediaType.equals("video")) {
                    return BidType.video;
                }
                return BidType.banner;
            }
            break;
        }
        throw new PreBidException(String.format("Failed to find impression for ID: '%s'", impId));
    }
}
