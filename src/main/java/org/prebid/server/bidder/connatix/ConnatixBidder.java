package org.prebid.server.bidder.connatix;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.prebid.server.bidder.Bidder;
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
import org.prebid.server.proto.openrtb.ext.request.connatix.ExtImpConnatix;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.net.URISyntaxException;
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

    private static final String BIDDER_CURRENCY = "USD";
    private static final String FORMATTING = "%s-%s";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    private final CurrencyConversionService currencyConversionService;

    public ConnatixBidder(String endpointUrl,
                          CurrencyConversionService currencyConversionService,
                          JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final Device device = request.getDevice();

        if (device == null
                || (device.getIp() == null && device.getIpv6() == null)) {
            return Result.withError(BidderError.badInput("Device IP is required"));
        }

        final List<BidderError> errors = new ArrayList<>();

        final String optimalEndpointUrl;
        try {
            optimalEndpointUrl = getOptimalEndpointUrl(request);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return Result.withErrors(errors);
        }


        final String displayManagerVer = buildDisplayManagerVersion(request);
        final MultiMap headers = resolveHeaders(device);

        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpConnatix extImpConnatix = parseExtImp(imp);
                final Imp modifiedImp = modifyImp(imp, extImpConnatix, displayManagerVer, request);

                httpRequests.add(makeHttpRequest(request, modifiedImp, headers, optimalEndpointUrl));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(httpRequests, errors);
    }

    private String getOptimalEndpointUrl(BidRequest request) {
        final String userId = getUserId(request);
        if (userId == null) {
            return endpointUrl;
        }

        final String dataCenterCode = getDataCenterCode(userId);
        if (dataCenterCode == null) {
            return endpointUrl;
        }

        try {
            final URIBuilder uriBuilder = new URIBuilder(endpointUrl);
            return uriBuilder.addParameter("dc", dataCenterCode).build().toString();
        } catch (URISyntaxException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private String getUserId(BidRequest request) {
        final User user = request.getUser();
        if (user == null) {
            return null;
        }

        final String buyerUid = user.getBuyeruid();
        if (buyerUid == null || buyerUid.isEmpty()) {
            return null;
        }

        return buyerUid.trim();
    }

    private String getDataCenterCode(String usedId) {
        if (usedId.startsWith("1-")) {
            return "us-east-2";
        } else if (usedId.startsWith("2-")) {
            return "us-west-2";
        } else if (usedId.startsWith("3-")) {
            return "eu-west-1";
        }

        return null;
    }

    private static String buildDisplayManagerVersion(BidRequest request) {
        return Optional.ofNullable(request.getApp())
                .map(App::getExt)
                .map(ExtApp::getPrebid)
                .filter(prebid -> ObjectUtils.allNotNull(prebid.getSource(), prebid.getVersion()))
                .map(prebid -> FORMATTING.formatted(prebid.getSource(), prebid.getVersion()))
                .orElse(StringUtils.EMPTY);
    }

    private static MultiMap resolveHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers();
        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIpv6());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
        }
        return headers;
    }

    private ExtImpConnatix parseExtImp(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), CONNATIX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Imp modifyImp(Imp imp, ExtImpConnatix extImpConnatix, String displayManagerVer, BidRequest request) {
        final Price bidFloorPrice = resolveBidFloor(imp, request);

        final ObjectNode impExt = mapper.mapper()
                .createObjectNode().set("connatix", mapper.mapper().valueToTree(extImpConnatix));

        return imp.toBuilder()
                .ext(impExt)
                .banner(modifyImpBanner(imp.getBanner()))
                .displaymanagerver(StringUtils.isBlank(imp.getDisplaymanagerver())
                        && StringUtils.isNotBlank(displayManagerVer)
                        ? displayManagerVer
                        : imp.getDisplaymanagerver())
                .bidfloor(bidFloorPrice.getValue())
                .bidfloorcur(bidFloorPrice.getCurrency())
                .build();
    }

    private Price resolveBidFloor(Imp imp, BidRequest bidRequest) {
        final Price initialBidFloorPrice = Price.of(imp.getBidfloorcur(), imp.getBidfloor());
        return BidderUtil.shouldConvertBidFloor(initialBidFloorPrice, BIDDER_CURRENCY)
                ? convertBidFloor(initialBidFloorPrice, bidRequest)
                : initialBidFloorPrice;
    }

    private Price convertBidFloor(Price bidFloorPrice, BidRequest bidRequest) {
        final BigDecimal convertedPrice = currencyConversionService.convertCurrency(
                bidFloorPrice.getValue(),
                bidRequest,
                bidFloorPrice.getCurrency(),
                BIDDER_CURRENCY);

        return Price.of(BIDDER_CURRENCY, convertedPrice);
    }

    private Banner modifyImpBanner(Banner banner) {
        if (banner == null) {
            return null;
        }

        if (banner.getW() == null && banner.getH() == null && CollectionUtils.isNotEmpty(banner.getFormat())) {
            final Format firstFormat = banner.getFormat().getFirst();
            return banner.toBuilder()
                    .w(firstFormat.getW())
                    .h(firstFormat.getH())
                    .build();
        }
        return banner;
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request, Imp imp, MultiMap headers, String optimalEndpointUrl) {
        final BidRequest outgoingRequest = request.toBuilder()
                .imp(List.of(imp))
                .build();

        return BidderUtil.defaultRequest(outgoingRequest, headers, optimalEndpointUrl, mapper);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderBid> bids = extractBids(bidResponse);

            return Result.withValues(bids);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid), BIDDER_CURRENCY))
                .toList();
    }

    private static BidType getBidType(Bid bid) {
        return Optional.ofNullable(bid.getExt())
                .map(ext -> ext.get("connatix"))
                .map(cnx -> cnx.get("mediaType"))
                .map(JsonNode::asText)
                .filter(type -> Objects.equals(type, "video"))
                .map(ignored -> BidType.video)
                .orElse(BidType.banner);
    }
}
