package org.prebid.server.bidder.optout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iabtcf.decoder.TCString;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.optout.model.AdSize;
import org.prebid.server.bidder.optout.model.CurrencySettings;
import org.prebid.server.bidder.optout.model.OptoutBid;
import org.prebid.server.bidder.optout.model.OptoutRequest;
import org.prebid.server.bidder.optout.model.OptoutResponse;
import org.prebid.server.bidder.optout.model.OptoutSlot;
import org.prebid.server.bidder.optout.model.Viewer;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.optout.ExtImpOptout;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.Uri;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class OptoutBidder implements Bidder<OptoutRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpOptout>> TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final TypeReference<List<OptoutBid>> OPTOUT_BID_LIST_TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final String DEFAULT_BID_CURRENCY = "EUR";
    private static final Integer DEFAULT_TTL = 300;
    private static final Integer OPTOUT_VENDOR_ID = 227;
    private static final String MATCHER_NAME = "PREBID";
    private static final String SDK_VERSION = "prebid-server";

    private final Uri endpointUrl;
    private final JacksonMapper mapper;

    public OptoutBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = Uri.of(endpointUrl);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<OptoutRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<OptoutSlot> slots = new ArrayList<>();
        String publisher = "";
        boolean includeOrtb2 = false;

        for (Imp imp : request.getImp()) {
            try {
                if (imp.getBanner() == null) {
                    throw new PreBidException("optout: imp %s is not banner".formatted(imp.getId()));
                }

                final ExtImpOptout extImp = parseImpExt(imp);
                publisher = getPublisher(publisher, extImp, imp.getId());

                slots.add(createSlot(imp, extImp));
                includeOrtb2 = getIncludeOrtb2(includeOrtb2, extImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (slots.isEmpty()) {
            return Result.withErrors(errors);
        }

        final String consent = readConsent(request);
        final boolean mayForwardPersonalData = mayForwardPersonalData(request, consent);
        final BidRequest forwardableRequest = makeForwardableORTB2(request, mayForwardPersonalData);

        final OptoutRequest optOutRequest = createOptOutRequest(publisher, slots, consent, request,
                forwardableRequest, mayForwardPersonalData, includeOrtb2);

        final HttpRequest<OptoutRequest> httpRequest = makeHttpRequest(request, optOutRequest, mayForwardPersonalData);
        return Result.of(Collections.singletonList(httpRequest), errors);
    }

    private ExtImpOptout parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("invalid impression extension: %s".formatted(e.getMessage()));
        }
    }

    private String getPublisher(String publisher, ExtImpOptout extImp, String impId) {
        if (StringUtils.isBlank(publisher)) {
            return extImp.getPublisher();
        } else if (!publisher.equals(extImp.getPublisher())) {
            throw new PreBidException("optout: imp %s has publisher %s, expected %s"
                    .formatted(impId, extImp.getPublisher(), publisher));
        }
        return publisher;
    }

    private OptoutSlot createSlot(Imp imp, ExtImpOptout extImp) {
        return OptoutSlot.builder()
                .adSlot(extImp.getAdSlot())
                .id(imp.getId())
                .sizes(getAllowedSizes(imp))
                .customs(normalizeCustoms(extImp.getCustoms()))
                .build();
    }

    private Boolean getIncludeOrtb2(Boolean includeOrtb2, ExtImpOptout extImp) {
        return includeOrtb2 || (extImp.getIncludeOrtb2() != null && extImp.getIncludeOrtb2());
    }

    private List<AdSize> getAllowedSizes(Imp imp) {
        final Banner banner = imp.getBanner();
        final Set<AdSize> uniqueSizes = new LinkedHashSet<>();

        if (banner.getW() != null && banner.getH() != null) {
            uniqueSizes.add(AdSize.builder()
                    .w(banner.getW())
                    .h(banner.getH())
                    .build());
        }

        if (banner.getFormat() != null) {
            for (Format format : banner.getFormat()) {
                if (format != null && format.getW() != null && format.getH() != null) {
                    uniqueSizes.add(AdSize.builder()
                            .w(banner.getW())
                            .h(banner.getH())
                            .build());
                }
            }
        }

        return new ArrayList<>(uniqueSizes);
    }

    private Map<String, String> normalizeCustoms(ObjectNode customs) {
        final Map<String, String> result = new HashMap<>();
        if (customs == null) {
            return result;
        }

        for (Map.Entry<String, JsonNode> entry : customs.properties()) {
            final String key = entry.getKey();
            final JsonNode value = entry.getValue();

            switch (value) {
                case null -> {
                }
                case NullNode _ -> {
                }
                case ArrayNode array -> {
                    final String joined = StreamSupport.stream(array.spliterator(), false)
                            .map(item -> item != null && !item.isNull() ? item.asText() : "")
                            .collect(Collectors.joining(","));
                    result.put(key, joined);
                }
                case ObjectNode obj -> {
                    try {
                        result.put(key, mapper.mapper().writeValueAsString(obj));
                    } catch (JsonProcessingException ignored) {
                    }
                }
                case TextNode text -> result.put(key, text.asText());
                default -> result.put(key, value.asText());
            }
        }
        return result;
    }

    private void mergeExtData(Map<String, String> customs, ObjectNode extData) {
        if (extData.isEmpty()) {
            return;
        }
        customs.putAll(normalizeCustoms(extData));
    }

    private String readConsent(BidRequest request) {
        if (request == null || request.getUser() == null) {
            return "";
        }

        if (StringUtils.isNotBlank(request.getUser().getConsent())) {
            return request.getUser().getConsent();
        }

        return request.getUser().getExt().getConsent();
    }

    private boolean mayForwardPersonalData(BidRequest request, String consent) {
        if (request == null || request.getRegs() == null || request.getRegs().getGdpr() == null) {
            return false;
        }

        if (request.getRegs().getGdpr() == 0) {
            return true;
        }
        if (StringUtils.isBlank(consent)) {
            return false;
        }
        final TCString tcString = decode(consent);
        if (tcString == null || tcString.getVersion() != 2) {
            return false;
        }
        return tcString.getPurposesConsent().contains(PurposeCode.TWO.code())
                && tcString.getVendorConsent().contains(OPTOUT_VENDOR_ID);
    }

    private static TCString decode(String consent) {
        try {
            return TCString.decode(consent);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private BidRequest makeForwardableORTB2(BidRequest request, boolean allowPersonalData) {
        if (!allowPersonalData) {
            final ObjectNode optoutProperties = mapper.mapper().createObjectNode().put("redacted", true);
            request.getUser().getExt().addProperty("optout", optoutProperties);

            final Device device = request.getDevice();
            return request.toBuilder()
                    .device(makeForwardableDeviceORTB2(device))
                    .build();
        }
        return request;
    }

    private Device makeForwardableDeviceORTB2(Device device) {
        if (device != null) {
            String ip = device.getIp();
            String ipv6 = device.getIpv6();

            if (ip != null && !ip.isEmpty()) {
                ip = "";
            }
            if (ipv6 != null && !ipv6.isEmpty()) {
                ipv6 = "";
            }
            return device.toBuilder()
                    .ip(ip)
                    .ipv6(ipv6)
                    .build();
        }
        return null;
    }

    private OptoutRequest createOptOutRequest(String publisherId,
                                              List<OptoutSlot> slots,
                                              String consent,
                                              BidRequest originRequest,
                                              BidRequest forwardableRequest,
                                              boolean mayForwardPersonalData,
                                              boolean includeOrtb2) {

        return OptoutRequest.builder()
                .publisher(publisherId)
                .slots(slots)
                .cur(getCurrencySettings(originRequest))
                .url(getPageUrl(originRequest))
                .sdkVersion(SDK_VERSION)
                .gdprConsent(consent)
                .gdpr(getGdpr(originRequest))
                .os(getDeviceOS(forwardableRequest))
                .viewer(getViewer(forwardableRequest, mayForwardPersonalData))
                .customs(getRequestCustomsData(originRequest, mayForwardPersonalData))
                .ortb2(getOrtb2(forwardableRequest, includeOrtb2))
                .build();
    }

    private String getPageUrl(BidRequest request) {
        String pageUrl = "";
        if (request.getSite() != null) {
            pageUrl = request.getSite().getPage();
        }
        return pageUrl;
    }

    private CurrencySettings getCurrencySettings(BidRequest request) {
        String currency = DEFAULT_BID_CURRENCY;
        if (request.getCur() != null && !request.getCur().isEmpty()) {
            currency = request.getCur().getFirst();
        }

        return CurrencySettings.builder()
                .adServerCurrency(currency)
                .granularityMultiplier(1)
                .build();
    }

    private Viewer getViewer(BidRequest forwardableRequest, boolean mayForwardPersonalData) {
        if (mayForwardPersonalData && forwardableRequest.getUser() != null
                && StringUtils.isNotBlank(forwardableRequest.getUser().getBuyeruid())) {
            return Viewer.builder()
                    .id(forwardableRequest.getUser().getBuyeruid())
                    .matcher(MATCHER_NAME)
                    .build();
        }
        return null;
    }

    private int getGdpr(BidRequest request) {
        if (request.getRegs() != null && request.getRegs().getGdpr() != null && request.getRegs().getGdpr() == 0) {
            return 0;
        }
        return 1;
    }

    private Map<String, String> getRequestCustomsData(BidRequest request, boolean mayForwardPersonalData) {
        final Map<String, String> requestCustoms = new HashMap<>();
        if (request.getSite() != null && request.getSite().getExt() != null) {
            mergeExtData(requestCustoms, request.getSite().getExt().getData());
        }
        if (request.getApp() != null && request.getApp().getExt() != null) {
            mergeExtData(requestCustoms, request.getApp().getExt().getData());
        }
        if (mayForwardPersonalData && request.getUser() != null && request.getUser().getExt() != null) {
            mergeExtData(requestCustoms, request.getUser().getExt().getData());
        }
        return requestCustoms;
    }

    private String getDeviceOS(BidRequest forwardableRequest) {
        if (forwardableRequest.getDevice() != null) {
            return forwardableRequest.getDevice().getOs();
        }
        return null;
    }

    private String getOrtb2(BidRequest forwardableRequest, boolean includeOrtb2) {
        if (includeOrtb2) {
            return mapper.encodeToString(forwardableRequest);
        }
        return null;
    }

    private HttpRequest<OptoutRequest> makeHttpRequest(BidRequest bidRequest,
                                                       OptoutRequest optOutRequest,
                                                       boolean mayForwardPersonalData) {

        return HttpRequest.<OptoutRequest>builder()
                .method(HttpMethod.POST)
                .uri(this.endpointUrl.toString())
                .impIds(BidderUtil.impIds(bidRequest))
                .headers(makeHeaders(bidRequest, mayForwardPersonalData))
                .payload(optOutRequest)
                .body(mapper.encodeToBytes(optOutRequest))
                .build();
    }

    private static MultiMap makeHeaders(BidRequest request, boolean mayForwardPersonalData) {
        final Device device = request.getDevice();
        final MultiMap headers = HttpUtil.headers();
        headers.add("Content-Type", "application/json");

        if (device != null) {
            if (StringUtils.isNotBlank(device.getUa())) {
                headers.add("X-Device-User-Agent", device.getUa());
            }
            if (mayForwardPersonalData) {
                String ip = device.getIp();
                if (StringUtils.isBlank(ip)) {
                    ip = device.getIpv6();
                }
                if (StringUtils.isNotBlank(ip)) {
                    headers.add("X-Forwarded-For", ip);
                }
            }
        }
        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<OptoutRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<OptoutSlot> slots = httpCall.getRequest().getPayload().getSlots();
            if (slots == null || slots.isEmpty()) {
                return Result.withError(BidderError.badServerResponse("optout: outgoing request has no slots"));
            }

            final List<BidderError> errors = new ArrayList<>();
            final List<BidderBid> bidderBids = new ArrayList<>();
            final Set<String> validImpIDs = slots.stream().map(OptoutSlot::getId).collect(Collectors.toSet());
            final List<OptoutBid> bids = parseOptoutResponse(httpCall.getResponse().getBody());
            String currency = "";

            for (OptoutBid optoutBid : bids) {
                if (!validImpIDs.contains(optoutBid.getRequestId())) {
                    errors.add(BidderError.badServerResponse(
                            "optout: unknown impression ID %s".formatted(optoutBid.getRequestId())));
                    continue;
                }

                if (isBidInvalid(optoutBid)) {
                    errors.add(BidderError.badServerResponse(
                            "optout: invalid bid for impression %s".formatted(optoutBid.getRequestId())));
                    continue;
                }

                if (StringUtils.isBlank(optoutBid.getBidId())) {
                    errors.add(BidderError.badServerResponse(
                            "optout: missing bid ID for impression %s".formatted(optoutBid.getRequestId())));
                }

                final String currencyFromBid = optoutBid.getCurrency();
                if (StringUtils.isNotBlank(currency) && !currency.equals(currencyFromBid)) {
                    errors.add(BidderError.badServerResponse(
                            "optout: mixed response currencies (%s and %s)".formatted(currency, currencyFromBid)));
                    continue;
                }
                currency = optoutBid.getCurrency();

                bidderBids.add(createBidderBid(optoutBid));
            }

            return Result.of(bidderBids, errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<OptoutBid> parseOptoutResponse(String responseBody) {
        try {
            return mapper.mapper().convertValue(responseBody, OPTOUT_BID_LIST_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            try {
                final List<OptoutBid> optoutBids = mapper.decodeValue(responseBody, OptoutResponse.class).getBids();
                if (optoutBids == null) {
                    throw new PreBidException("optout: DSP response is missing bids array");
                }
                return optoutBids;
            } catch (DecodeException ex) {
                throw new PreBidException("optout: invalid response JSON: %s".formatted(e.getMessage()));
            }
        }
    }

    private boolean isBidInvalid(OptoutBid bid) {
        return StringUtils.isBlank(bid.getCurrency()) || StringUtils.isBlank(bid.getAd()) || bid.getWidth() <= 0
                || bid.getHeight() <= 0 || bid.getCpm() == null || bid.getCpm().compareTo(BigDecimal.ZERO) <= 0;
    }

    private BidderBid createBidderBid(OptoutBid optoutBid) {
        final Bid bid = Bid.builder()
                .id(optoutBid.getBidId())
                .impid(optoutBid.getRequestId())
                .price(optoutBid.getCpm())
                .adm(optoutBid.getAd())
                .w(optoutBid.getWidth())
                .h(optoutBid.getHeight())
                .crid(optoutBid.getCreativeId())
                .exp(getTtl(optoutBid))
                .ext(getOptoutBidExt(optoutBid))
                .build();

        return BidderBid.of(bid, BidType.banner, getBidCurrency(optoutBid.getCurrency()));
    }

    private ObjectNode getOptoutBidExt(OptoutBid optoutBid) {
        if (optoutBid.getOptOutExt() != null && !optoutBid.getOptOutExt().isEmpty()) {
            return optoutBid.getOptOutExt();
        }
        return null;
    }

    private Integer getTtl(OptoutBid optoutBid) {
        final Integer ttl = optoutBid.getTtl();
        if (ttl == null || ttl <= 0) {
            return DEFAULT_TTL;
        }
        return ttl;
    }

    private String getBidCurrency(String currency) {
        if (StringUtils.isBlank(currency)) {
            return DEFAULT_BID_CURRENCY;
        }
        return currency;
    }
}
