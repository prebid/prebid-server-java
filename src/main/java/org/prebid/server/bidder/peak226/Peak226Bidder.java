package org.prebid.server.bidder.peak226;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.peak226.model.Peak226Response;
import org.prebid.server.bidder.peak226.model.Peak226SeatBid;
import org.prebid.server.bidder.peak226.model.RegionPublisher;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.peak226.ExtImpPeak226;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.Uri;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class Peak226Bidder implements Bidder<BidRequest> {

    private static final String USD_CURRENCY = "USD";
    private static final String DEFAULT_REGION = "us";
    private static final String ZERO_IFA = "00000000-0000-0000-0000-000000000000";
    private static final String REGION_MACRO = "Region";
    private static final String PRICE_MACRO = "${AUCTION_PRICE}";

    private static final TypeReference<ExtPrebid<?, ExtImpPeak226>> EXT_TYPE_REFERENCE = new TypeReference<>() {
    };
    private final Uri endpointUrl;
    private final CurrencyConversionService currencyConversionService;
    private final JacksonMapper mapper;

    public Peak226Bidder(String endpointUrl,
                         CurrencyConversionService currencyConversionService,
                         JacksonMapper mapper) {
        this.endpointUrl = Uri.of(endpointUrl);
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final Map<RegionPublisher, List<Imp>> impsByRegion = new LinkedHashMap<>();
        final List<HttpRequest<BidRequest>> result = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                modifyAndMapImp(imp, bidRequest, impsByRegion);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (impsByRegion.isEmpty()) {
            return Result.empty();
        }

        final Device device = sanitizeDevice(bidRequest.getDevice());

        for (RegionPublisher regionPublisher : impsByRegion.keySet()) {
            final List<Imp> imps = impsByRegion.get(regionPublisher);

            final HttpRequest<BidRequest> singleRequest = createSingleRequest(
                    imps, bidRequest, regionPublisher, device);

            result.add(singleRequest);
        }

        return Result.of(result, errors);
    }

    private void modifyAndMapImp(Imp imp,
                          BidRequest bidRequest,
                          Map<RegionPublisher, List<Imp>> impsByRegion) {

        final ExtImpPeak226 impExt = parseImpExt(imp);

        final Price impFloorPrice = getBidFloor(imp, bidRequest);

        final String region = StringUtils.isBlank(impExt.getRegion())
                ? DEFAULT_REGION
                : impExt.getRegion().toLowerCase(Locale.ROOT);

        final RegionPublisher regionPublisher = RegionPublisher.of(region, impExt.getPublisherId());
        final Imp modifiedImp = imp.toBuilder()
                .ext(stripBidderExt(imp))
                .tagid(impExt.getPlacementId())
                .bidfloor(impFloorPrice.getValue())
                .bidfloorcur(impFloorPrice.getCurrency())
                .build();
        impsByRegion.computeIfAbsent(regionPublisher, _ -> new ArrayList<>())
                .add(modifiedImp);
    }

    private ExtImpPeak226 parseImpExt(Imp imp) throws PreBidException {
        try {
            return mapper.mapper().convertValue(imp.getExt(), EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Invalid ext. Imp.Id: " + imp.getId());
        }
    }

    private ObjectNode stripBidderExt(Imp imp) {
        final ObjectNode modifiedExt = imp.getExt().deepCopy();
        modifiedExt.remove("bidder");
        return modifiedExt;
    }

    private Price getBidFloor(Imp imp, BidRequest bidRequest) {
        final Price price = Price.of(imp.getBidfloorcur(), imp.getBidfloor());
        if (BidderUtil.shouldConvertBidFloor(price, USD_CURRENCY)) {
            return convertBidFloor(price, bidRequest);
        }
        return Price.of(
                StringUtils.defaultIfBlank(price.getCurrency(), USD_CURRENCY),
                price.getValue());
    }

    private Price convertBidFloor(Price bidFloorPrice, BidRequest bidRequest) {
        final BigDecimal convertedPrice = currencyConversionService.convertCurrency(
                bidFloorPrice.getValue(),
                bidRequest,
                bidFloorPrice.getCurrency(),
                USD_CURRENCY);

        return Price.of(USD_CURRENCY, convertedPrice);
    }

    private Device sanitizeDevice(Device device) {
        if (device == null || !ZERO_IFA.equals(device.getIfa())) {
            return device;
        }
        return device.toBuilder()
                .ifa("")
                .build();
    }

    private HttpRequest<BidRequest> createSingleRequest(List<Imp> imps,
                                                        BidRequest request,
                                                        RegionPublisher regionPublisher,
                                                        Device device) {

        final BidRequest modifiedRequest = modifyRequest(imps, request, regionPublisher.getPublisherId(), device);
        return BidderUtil.defaultRequest(modifiedRequest, resolveUrl(regionPublisher.getRegion()), mapper);
    }

    private BidRequest modifyRequest(List<Imp> imps, BidRequest request, String publisherId, Device device) {
        final Site site = request.getSite();
        final App app = request.getApp();

        return request.toBuilder()
                .device(device)
                .imp(imps)
                .site(site != null ? modifySite(site, publisherId) : null)
                .app(app != null ? modifyApp(app, publisherId) : null)
                .build();
    }

    private static Site modifySite(Site site, String publisherId) {
        return site.toBuilder().publisher(modifyPublisher(site.getPublisher(), publisherId)).build();
    }

    private static App modifyApp(App app, String publisherId) {
        return app.toBuilder().publisher(modifyPublisher(app.getPublisher(), publisherId)).build();
    }

    private static Publisher modifyPublisher(Publisher publisher, String publisherId) {
        return Optional.ofNullable(publisher)
                .map(Publisher::toBuilder)
                .orElseGet(Publisher::builder)
                .id(publisherId)
                .build();
    }

    private String resolveUrl(String region) {
        return endpointUrl.replaceMacro(REGION_MACRO, region).expand();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final Peak226Response bidResponse =
                    mapper.decodeValue(httpCall.getResponse().getBody(), Peak226Response.class);
            return extractBids(bidResponse);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    public Result<List<BidderBid>> extractBids(Peak226Response bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Result.empty();
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bidderBids = new ArrayList<>();
        for (Peak226SeatBid seatBid : bidResponse.getSeatbid()) {
            if (CollectionUtils.isEmpty(seatBid.getBid())) {
                continue;
            }

            for (ObjectNode bid : seatBid.getBid()) {
                try {
                    bidderBids.add(createBidderBid(bid, bidResponse.getCur()));
                } catch (PreBidException e) {
                    errors.add(BidderError.badServerResponse(e.getMessage()));
                }
            }
        }

        return Result.of(bidderBids, errors);
    }

    private BidderBid createBidderBid(ObjectNode bidNode, String currency) {
        try {
            final Bid bid = mapper.mapper().treeToValue(bidNode, Bid.class);
            final Bid modifiedBid = bid.toBuilder().adm(getAdm(bidNode, bid)).build();

            return BidderBid.of(resolveMacros(modifiedBid), getMediaTypeForBid(bid), currency);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private String getAdm(ObjectNode bidNode, Bid bid) {
        final JsonNode admNative = bidNode.at("/adm_native");
        final String bidAdm = bid.getAdm();

        if (admNative != null && !admNative.isEmpty() && StringUtils.isBlank(bidAdm)) {
            return mapper.encodeToString(admNative);
        }

        return bidAdm;
    }

    private Bid resolveMacros(Bid bid) {
        final BigDecimal price = bid.getPrice();
        final String priceAsString = price != null ? price.toPlainString() : "0";

        return bid.toBuilder()
                .adm(Strings.CS.replace(bid.getAdm(), PRICE_MACRO, priceAsString))
                .nurl(Strings.CS.replace(bid.getNurl(), PRICE_MACRO, priceAsString))
                .burl(Strings.CS.replace(bid.getBurl(), PRICE_MACRO, priceAsString))
                .build();
    }

    private static BidType getMediaTypeForBid(Bid bid) {
        final Integer mType = bid.getMtype();
        return switch (mType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 4 -> BidType.xNative;
            case null, default -> throw new PreBidException(
                    "Unrecognized bid type for impression %s".formatted(bid.getImpid()));
        };
    }
}
