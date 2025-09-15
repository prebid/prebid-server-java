package org.prebid.server.bidder.showheroes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.showheroes.ExtImpShowheroes;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.version.PrebidVersionProvider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ShowheroesBidder implements Bidder<BidRequest> {

    private static final String BID_CURRENCY = "EUR";
    private static final String DEFAULT_ORTB_CURRENCY = "USD";
    private static final String PBSP_JAVA = "java";
    private static final TypeReference<ExtPrebid<?, ExtImpShowheroes>> SHOWHEROES_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final CurrencyConversionService currencyConversionService;
    private final JacksonMapper mapper;
    private final String pbsVersion;

    public ShowheroesBidder(String endpointUrl,
            CurrencyConversionService currencyConversionService,
            PrebidVersionProvider prebidVersionProvider,
            JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.mapper = Objects.requireNonNull(mapper);

        this.pbsVersion = prebidVersionProvider.getNameVersionRecord();
    }

    private BidderError validate(BidRequest bidRequest) {
        // request must contain site object with page or app object with bundle
        if (bidRequest.getSite() == null && bidRequest.getApp() == null) {
            return BidderError.badInput("BidRequest must contain one of site or app");
        }
        if (bidRequest.getSite() != null && bidRequest.getSite().getPage() == null) {
            return BidderError.badInput("BidRequest.site.page is required");
        }
        if (bidRequest.getApp() != null && bidRequest.getApp().getBundle() == null) {
            return BidderError.badInput("BidRequest.app.bundle is required");
        }
        return null;
    }

    private ExtRequestPrebidChannel getPrebidChannel(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getExt())
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getChannel)
                .orElse(null);
    }

    private Imp processImpression(BidRequest bidRequest, Imp imp, ExtRequestPrebidChannel prebidChannel) {
        if (imp.getBanner() == null && imp.getVideo() == null) {
            throw new PreBidException("Impression must contain one of banner or video");
        }

        final ExtImpShowheroes extImpShowheroes = mapper.mapper()
                .convertValue(imp.getExt(), SHOWHEROES_EXT_TYPE_REFERENCE).getBidder();
        if (extImpShowheroes == null || extImpShowheroes.getUnitId() == null
                || extImpShowheroes.getUnitId().isBlank()) {
            throw new PreBidException("unitId is required");
        }

        String channelName = null;
        String channelVersion = null;
        if (prebidChannel != null) {
            channelName = prebidChannel.getName();
            channelVersion = prebidChannel.getVersion();
        }

        final ObjectNode impExt = imp.getExt();

        final Imp.ImpBuilder impBuilder = imp.toBuilder();

        // copy unitId from ext.bidder to ext.params
        impExt.set("params", JsonNodeFactory.instance.objectNode()
                .put("unitId", extImpShowheroes.getUnitId()));

        impBuilder.ext(impExt);
        if (imp.getDisplaymanager() == null && channelName != null) {
            impBuilder.displaymanager(channelName);
            impBuilder.displaymanagerver(channelVersion);
        }

        String currency = imp.getBidfloorcur();
        // if floor price is 0, or currency is EUR - no need to convert
        if (imp.getBidfloor() == null || imp.getBidfloor().compareTo(BigDecimal.ZERO) == 0
                || currency == BID_CURRENCY) {
            return impBuilder.build();
        }
        if (currency != null && !currency.isBlank()) {
            // if not provided default currency is USD
            currency = DEFAULT_ORTB_CURRENCY;
        }

        final BigDecimal eurFloor = currencyConversionService.convertCurrency(
                imp.getBidfloor(), bidRequest, currency, BID_CURRENCY);
        return impBuilder
                .bidfloorcur(BID_CURRENCY)
                .bidfloor(eurFloor)
                .build();
    }

    private Source getPBSSource(BidRequest bidRequest) {
        Source source = bidRequest.getSource();
        if (source == null) {
            source = Source.builder().build();
        }

        ExtSource extSource = source.getExt();
        if (extSource == null) {
            extSource = ExtSource.of(null);
        }

        JsonNode prebidExt = extSource.getProperty("pbs");
        if (prebidExt == null || !prebidExt.isObject()) {
            prebidExt = mapper.mapper().createObjectNode();
        }

        ((ObjectNode) prebidExt).put("pbsv", pbsVersion).put("pbsp", PBSP_JAVA);

        extSource.addProperty("pbs", prebidExt);

        return source.toBuilder().ext(extSource).build();
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final BidderError validationError = validate(request);
        if (validationError != null) {
            return Result.of(Collections.emptyList(), List.of(validationError));
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        final ExtRequestPrebidChannel prebidChannel = getPrebidChannel(request);
        final List<Imp> modifiedImps = new ArrayList<>(request.getImp().size());

        for (Imp impression : request.getImp()) {
            try {
                modifiedImps.add(processImpression(request, impression, prebidChannel));
            } catch (Exception e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }
        }

        if (modifiedImps.isEmpty()) {
            return Result.of(httpRequests, errors);
        }
        Source source = request.getSource();
        if (pbsVersion != null) {
            source = getPBSSource(request);
        }

        httpRequests.add(makeHttpRequest(request.toBuilder().imp(modifiedImps).source(source).build()));
        return Result.of(httpRequests, errors);
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request) {
        return BidderUtil.defaultRequest(request, endpointUrl, mapper);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;
        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode == 204) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }
        if (statusCode != 200) {
            return Result.withError(BidderError.badServerResponse(
                    "Unexpected status code: " + statusCode));
        }

        try {
            bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        return Result.of(extractBids(bidResponse), Collections.emptyList());
    }

    private List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid), bidResponse.getCur()))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidType getBidType(Bid bid) {
        return switch (bid.getMtype()) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case null, default -> BidType.video; // if not provided video is assumed
        };
    }
}
