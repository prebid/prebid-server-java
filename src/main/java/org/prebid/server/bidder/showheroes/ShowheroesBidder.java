package org.prebid.server.bidder.showheroes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
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

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final BidderError validationError = validate(request.getSite(), request.getApp());
        if (validationError != null) {
            return Result.withError(validationError);
        }

        final List<BidderError> errors = new ArrayList<>();

        final ExtRequestPrebidChannel prebidChannel = getPrebidChannel(request);
        final List<Imp> modifiedImps = new ArrayList<>(request.getImp().size());

        for (Imp impression : request.getImp()) {
            try {
                modifiedImps.add(modifyImp(request, impression, prebidChannel));
            } catch (Exception e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (modifiedImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final Source source = modifySource(request);
        final BidRequest modifiedRequest = request.toBuilder().imp(modifiedImps).source(source).build();
        final HttpRequest<BidRequest> httpRequest = BidderUtil.defaultRequest(modifiedRequest, endpointUrl, mapper);

        return Result.of(Collections.singletonList(httpRequest), errors);
    }

    private static BidderError validate(Site site, App app) {
        if (site == null && app == null) {
            return BidderError.badInput("BidRequest must contain one of site or app");
        }
        if (site != null && site.getPage() == null) {
            return BidderError.badInput("BidRequest.site.page is required");
        }
        if (app != null && app.getBundle() == null) {
            return BidderError.badInput("BidRequest.app.bundle is required");
        }
        return null;
    }

    private static ExtRequestPrebidChannel getPrebidChannel(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getExt())
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getChannel)
                .orElse(null);
    }

    private Imp modifyImp(BidRequest bidRequest, Imp imp, ExtRequestPrebidChannel prebidChannel) {
        final ExtImpShowheroes extImpShowheroes = parseImpExt(imp);

        final Imp.ImpBuilder impBuilder = imp.toBuilder();

        if (prebidChannel != null && imp.getDisplaymanager() == null) {
            impBuilder.displaymanager(prebidChannel.getName());
            impBuilder.displaymanagerver(prebidChannel.getVersion());
        }

        impBuilder.ext(modifyImpExt(imp, extImpShowheroes));

        if (!shouldConvertFloor(imp)) {
            return impBuilder.build();
        }

        return impBuilder
                .bidfloorcur(BID_CURRENCY)
                .bidfloor(resolveBidFloor(bidRequest, imp))
                .build();
    }

    private ExtImpShowheroes parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), SHOWHEROES_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private ObjectNode modifyImpExt(Imp imp, ExtImpShowheroes shImpExt) {
        final ObjectNode impExt = ObjectUtils.defaultIfNull(imp.getExt(), mapper.mapper().createObjectNode());
        impExt.set("params", mapper.mapper().createObjectNode().put("unitId", shImpExt.getUnitId()));
        return impExt;
    }

    private static boolean shouldConvertFloor(Imp imp) {
        return BidderUtil.isValidPrice(imp.getBidfloor())
                && !StringUtils.equalsIgnoreCase(imp.getBidfloorcur(), BID_CURRENCY);
    }

    private BigDecimal resolveBidFloor(BidRequest bidRequest, Imp imp) {
        return currencyConversionService.convertCurrency(
                imp.getBidfloor(), bidRequest, imp.getBidfloorcur(), BID_CURRENCY);
    }

    private Source modifySource(BidRequest bidRequest) {
        if (pbsVersion == null) {
            return bidRequest.getSource();
        }
        final Source source = ObjectUtils.defaultIfNull(bidRequest.getSource(), Source.builder().build());
        final ExtSource extSource = ObjectUtils.defaultIfNull(source.getExt(), ExtSource.of(null));
        final ObjectNode prebidExtSource = Optional.ofNullable(extSource.getProperty("pbs"))
                .filter(JsonNode::isObject)
                .map(ObjectNode.class::cast)
                .orElseGet(() -> mapper.mapper().createObjectNode())
                .put("pbsv", pbsVersion)
                .put("pbsp", PBSP_JAVA);

        extSource.addProperty("pbs", prebidExtSource);
        return source.toBuilder().ext(extSource).build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

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
            case null, default -> BidType.video;
        };
    }
}
