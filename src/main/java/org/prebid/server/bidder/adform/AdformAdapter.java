package org.prebid.server.bidder.adform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Device;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.adform.model.AdformBid;
import org.prebid.server.bidder.adform.model.AdformDigitrust;
import org.prebid.server.bidder.adform.model.AdformParams;
import org.prebid.server.bidder.adform.model.UrlParameters;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.MediaType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Adform {@link Adapter} implementation.
 */
public class AdformAdapter implements Adapter<Void, List<AdformBid>> {

    private static final String VERSION = "0.1.3";
    private static final String DEFAULT_CURRENCY = "USD";

    private final String cookieFamilyName;
    private final String endpointUrl;
    private final JacksonMapper mapper;

    private final AdformRequestUtil requestUtil;
    private final AdformHttpUtil httpUtil;

    public AdformAdapter(String cookieFamilyName, String endpointUrl, JacksonMapper mapper) {
        this.cookieFamilyName = Objects.requireNonNull(cookieFamilyName);
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);

        this.requestUtil = new AdformRequestUtil(mapper);
        this.httpUtil = new AdformHttpUtil(mapper);
    }

    /**
     * Creates {@link AdapterHttpRequest} with GET http method and all parameters in url and headers, with empty body.
     */
    @Override
    public List<AdapterHttpRequest<Void>> makeHttpRequests(AdapterRequest adapterRequest,
                                                           PreBidRequestContext preBidRequestContext) {
        final List<AdformParams> adformParams = adapterRequest.getAdUnitBids().stream()
                .map(this::toAdformParams)
                .collect(Collectors.toList());

        final ExtUser extUser = requestUtil.getExtUser(preBidRequestContext.getPreBidRequest().getUser());

        return Collections.singletonList(AdapterHttpRequest.of(
                HttpMethod.GET,
                getUrl(preBidRequestContext, adformParams, extUser),
                null,
                headers(preBidRequestContext, requestUtil.getAdformDigitrust(extUser))));
    }

    @Override
    public List<Bid.BidBuilder> extractBids(AdapterRequest adapterRequest,
                                            ExchangeCall<Void, List<AdformBid>> exchangeCall) throws PreBidException {
        return toBidBuilder(exchangeCall.getResponse(), adapterRequest);
    }

    @Override
    public boolean tolerateErrors() {
        return false;
    }

    @Override
    public TypeReference<List<AdformBid>> responseTypeReference() {
        return new TypeReference<List<AdformBid>>() {
        };
    }

    /**
     * Converts {@link AdUnitBid} to masterTagId. In case of any problem to retrieve or validate masterTagId, throws
     * {@link PreBidException}
     */
    private AdformParams toAdformParams(AdUnitBid adUnitBid) {
        final ObjectNode params = adUnitBid.getParams();
        if (params == null) {
            throw new PreBidException("Adform params section is missing");
        }
        final AdformParams adformParams;
        try {
            adformParams = mapper.mapper().treeToValue(params, AdformParams.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage(), e.getCause());
        }
        final Long masterTagId = adformParams.getMid();
        if (masterTagId != null && masterTagId > 0) {
            return adformParams;
        } else {
            throw new PreBidException(String.format("master tag(placement) id is invalid=%s", masterTagId));
        }
    }

    /**
     * Creates adform url with parameters
     */
    private String getUrl(PreBidRequestContext preBidRequestContext, List<AdformParams> adformParams, ExtUser extUser) {
        final Integer secure = preBidRequestContext.getSecure();
        final Device device = preBidRequestContext.getPreBidRequest().getDevice();
        return httpUtil.buildAdformUrl(
                UrlParameters.builder()
                        .masterTagIds(getMasterIds(adformParams))
                        .keyValues(getKeyValues(adformParams))
                        .keyWords(getKeyWords(adformParams))
                        .priceTypes(getPriceTypes(adformParams))
                        .endpointUrl(endpointUrl)
                        .tid(ObjectUtils.firstNonNull(preBidRequestContext.getPreBidRequest().getTid(), ""))
                        .ip(ObjectUtils.firstNonNull(preBidRequestContext.getIp(), ""))
                        .advertisingId(device != null ? device.getIfa() : "")
                        .secure(secure != null && secure == 1)
                        .gdprApplies(requestUtil.getGdprApplies(preBidRequestContext.getPreBidRequest().getRegs()))
                        .consent(requestUtil.getConsent(extUser))
                        .currency(DEFAULT_CURRENCY)
                        .build());
    }

    /**
     * Converts {@link AdformParams} {@link List} to master ids {@link List}
     */
    private List<Long> getMasterIds(List<AdformParams> adformParams) {
        return adformParams.stream().map(AdformParams::getMid).collect(Collectors.toList());
    }

    /**
     * Converts {@link AdformParams} {@link List} to key values {@link List}
     */
    private List<String> getKeyValues(List<AdformParams> adformParams) {
        return adformParams.stream().map(AdformParams::getKeyValues).collect(Collectors.toList());
    }

    /**
     * Converts {@link AdformParams} {@link List} to key words {@link List}
     */
    private List<String> getKeyWords(List<AdformParams> adformParams) {
        return adformParams.stream().map(AdformParams::getKeyWords).collect(Collectors.toList());
    }

    /**
     * Converts {@link AdformParams} {@link List} to price types {@link List}
     */
    private List<String> getPriceTypes(List<AdformParams> adformParams) {
        return adformParams.stream().map(AdformParams::getPriceType).collect(Collectors.toList());
    }

    /**
     * Creates adform headers, which stores adform request parameters
     */
    private MultiMap headers(PreBidRequestContext preBidRequestContext, AdformDigitrust adformDigitrust) {
        return httpUtil.buildAdformHeaders(
                VERSION,
                ObjectUtils.firstNonNull(preBidRequestContext.getUa(), ""),
                ObjectUtils.firstNonNull(preBidRequestContext.getIp(), ""),
                preBidRequestContext.getReferer(),
                preBidRequestContext.getUidsCookie().uidFrom(cookieFamilyName),
                adformDigitrust);
    }

    /**
     * Coverts response {@link AdformBid} to {@link Bid} by matching them with {@link AdUnitBid}
     */
    private static List<Bid.BidBuilder> toBidBuilder(List<AdformBid> adformBids, AdapterRequest adapterRequest) {
        final List<AdUnitBid> adUnitBids = adapterRequest.getAdUnitBids();
        final List<Bid.BidBuilder> bidBuilders = new ArrayList<>();
        for (int i = 0; i < adformBids.size(); i++) {
            final AdformBid adformBid = adformBids.get(i);
            final String banner = adformBid.getBanner();
            if (StringUtils.isNotEmpty(banner) && Objects.equals(adformBid.getResponse(), "banner")) {
                final AdUnitBid adUnitBid = adUnitBids.get(i);
                bidBuilders.add(Bid.builder()
                        .bidId(adUnitBid.getBidId())
                        .code(adUnitBid.getAdUnitCode())
                        .bidder(adapterRequest.getBidderCode())
                        .price(adformBid.getWinBid())
                        .adm(banner)
                        .width(adformBid.getWidth())
                        .height(adformBid.getHeight())
                        .dealId(adformBid.getDealId())
                        .creativeId(adformBid.getWinCrid())
                        .mediaType(MediaType.banner)
                );
            }
        }
        return bidBuilders;
    }
}
