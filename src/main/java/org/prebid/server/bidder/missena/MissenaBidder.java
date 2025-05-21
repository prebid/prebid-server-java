package org.prebid.server.bidder.missena;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.missena.ExtImpMissena;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.version.PrebidVersionProvider;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class MissenaBidder implements Bidder<MissenaAdRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpMissena>> TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final String USD_CURRENCY = "USD";
    private static final String EUR_CURRENCY = "EUR";
    private static final String PUBLISHER_ID_MACRO = "{{PublisherID}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;
    private final CurrencyConversionService currencyConversionService;
    private final PrebidVersionProvider prebidVersionProvider;

    public MissenaBidder(String endpointUrl,
                         JacksonMapper mapper,
                         CurrencyConversionService currencyConversionService,
                         PrebidVersionProvider prebidVersionProvider) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.prebidVersionProvider = Objects.requireNonNull(prebidVersionProvider);
    }

    @Override
    public Result<List<HttpRequest<MissenaAdRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpMissena extImp = parseImpExt(imp);
                final HttpRequest<MissenaAdRequest> httpRequest = makeHttpRequest(request, imp, extImp);
                return Result.of(Collections.singletonList(httpRequest), errors);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        return Result.withErrors(errors);
    }

    private ExtImpMissena parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Error parsing missenaExt parameters");
        }
    }

    private HttpRequest<MissenaAdRequest> makeHttpRequest(BidRequest request, Imp imp, ExtImpMissena extImp) {
        final Site site = request.getSite();
        final String pageUrl = site != null ? site.getPage() : null;
        final User user = request.getUser();
        final Regs regs = request.getRegs();
        final Device device = request.getDevice();
        final Source source = request.getSource();

        final String requestCurrency = resolveCurrency(request.getCur());
        final Price floorInfo = resolveBidFloor(imp, request, requestCurrency);

        final MissenaUserParams userParams = MissenaUserParams.builder()
                .formats(extImp.getFormats())
                .placement(extImp.getPlacement())
                .testMode(extImp.getTestMode())
                .settings(extImp.getSettings())
                .build();

        final MissenaAdRequest missenaAdRequest = MissenaAdRequest.builder()
                .adUnit(imp.getId())
                .buyerUid(user != null ? user.getBuyeruid() : null)
                .coppa(regs != null ? regs.getCoppa() : null)
                .currency(requestCurrency)
                .userEids(user != null ? user.getEids() : null)
                .floor(floorInfo.getValue())
                .floorCurrency(floorInfo.getCurrency())
                .gdpr(isGdpr(regs))
                .gdprConsent(getUserConsent(user))
                .idempotencyKey(request.getId())
                .referer(pageUrl)
                .refererCanonical(site != null ? site.getDomain() : null)
                .requestId(request.getId())
                .schain(source != null ? source.getSchain() : null)
                .timeout(request.getTmax())
                .params(userParams)
                .version(prebidVersionProvider.getNameVersionRecord())
                .build();

        return HttpRequest.<MissenaAdRequest>builder()
                .method(HttpMethod.POST)
                .uri(resolveEndpointUrl(extImp.getApiKey()))
                .headers(makeHeaders(device, site))
                .impIds(Collections.singleton(imp.getId()))
                .body(mapper.encodeToBytes(missenaAdRequest))
                .payload(missenaAdRequest)
                .build();
    }

    private Price resolveBidFloor(Imp imp, BidRequest bidRequest, String targetCurrency) {
        final Price initialBidFloorPrice = Price.of(imp.getBidfloorcur(), imp.getBidfloor());
        return BidderUtil.isValidPrice(initialBidFloorPrice)
                ? convertBidFloor(initialBidFloorPrice, imp.getId(), bidRequest, targetCurrency)
                : initialBidFloorPrice;
    }

    private Price convertBidFloor(Price bidFloorPrice, String impId, BidRequest bidRequest, String targetCurrency) {
        final String bidFloorCur = bidFloorPrice.getCurrency();

        if (targetCurrency.equalsIgnoreCase(bidFloorCur)) {
            return bidFloorPrice;
        }

        try {
            final BigDecimal convertedPrice = currencyConversionService
                    .convertCurrency(bidFloorPrice.getValue(), bidRequest, bidFloorCur, targetCurrency);

            return Price.of(targetCurrency, convertedPrice);
        } catch (PreBidException e) {
            throw new PreBidException("Unable to convert provided bid floor currency from %s to %s for imp `%s`"
                    .formatted(bidFloorCur, targetCurrency, impId));
        }
    }

    private String resolveCurrency(List<String> requestCurrencies) {
        for (String currency : requestCurrencies) {
            if (USD_CURRENCY.equalsIgnoreCase(currency)) {
                return USD_CURRENCY;
            }
        }

        for (String currency : requestCurrencies) {
            if (EUR_CURRENCY.equalsIgnoreCase(currency)) {
                return EUR_CURRENCY;
            }
        }

        return USD_CURRENCY;
    }

    private MultiMap makeHeaders(Device device, Site site) {
        final MultiMap headers = HttpUtil.headers();

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIpv6());
        }

        if (site != null && StringUtils.isNotBlank(site.getPage())) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.REFERER_HEADER, site.getPage());
            try {
                final URL url = new URL(site.getPage());
                final String origin = url.getProtocol() + "://" + url.getHost();
                HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ORIGIN_HEADER, origin);
            } catch (MalformedURLException e) {
                // do nothing
            }
        }
        return headers;
    }

    private String resolveEndpointUrl(String apiKey) {
        return endpointUrl.replace(PUBLISHER_ID_MACRO, HttpUtil.encodeUrl(apiKey));
    }

    private static boolean isGdpr(Regs regs) {
        return Optional.ofNullable(regs)
                .map(Regs::getExt)
                .map(ExtRegs::getGdpr)
                .map(gdpr -> gdpr == 1)
                .orElse(false);
    }

    private static String getUserConsent(User user) {
        return Optional.ofNullable(user)
                .map(User::getExt)
                .map(ExtUser::getConsent)
                .filter(StringUtils::isNotBlank)
                .orElse(null);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<MissenaAdRequest> httpCall, BidRequest bidRequest) {
        try {
            final MissenaAdResponse bidResponse = mapper.decodeValue(
                    httpCall.getResponse().getBody(),
                    MissenaAdResponse.class);
            return Result.withValues(Collections.singletonList(extractBid(bidRequest, bidResponse)));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private BidderBid extractBid(BidRequest request, MissenaAdResponse response) {
        final Bid bid = Bid.builder()
                .id(request.getId())
                .price(response.getCpm())
                .impid(request.getImp().getFirst().getId())
                .adm(response.getAd())
                .crid(response.getRequestId())
                .build();

        return BidderBid.of(bid, BidType.banner, response.getCurrency());
    }
}
