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
import com.iabtcf.decoder.TCString;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import lombok.Builder;
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
import org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.optout.ExtImpOptout;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.Uri;

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

public class OptoutBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpOptout>> TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final String DEFAULT_BID_CURRENCY = "EUR";
    private static final Integer DEFAULT_TTL = 300;
    private static final Integer OPTOUT_VENDOR_ID = 227;
    private static final String MATCHER_NAME = "PREBID";

    private final Uri endpointUrl;
    private final JacksonMapper mapper;

    public OptoutBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = Uri.of(endpointUrl);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<OptoutSlot> slots = new ArrayList<>();
        String publisherId = "";
        boolean includeOrtb2 = false;

        for (Imp imp : request.getImp()) {
            try {
                if (imp.getBanner() == null) {
                    throw new PreBidException("optout: imp %s is not banner".formatted(imp.getId()));
                }

                final ExtImpOptout extImp = parseImpExt(imp);

                if (StringUtils.isBlank(publisherId)) {
                    publisherId = extImp.getPublisher();
                } else if (extImp.getPublisher().equals(publisherId)) {
                    throw new PreBidException("optout: imp %s has publisher %s, expected %s"
                            .formatted(imp.getId(), extImp.getPublisher(), publisherId));
                }

                slots.add(OptoutSlot.builder()
                        .adSlot(extImp.getAdSlot())
                        .id(imp.getId())
                        .sizes(getAllowedSizes(imp))
                        .customs(normalizeCustoms(extImp.getCustoms()))
                        .build());
                includeOrtb2 = includeOrtb2 || extImp.getIncludeOrtb2();
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (slots.isEmpty()) {
            return Result.withErrors(errors);
        }

        int gdpr = 1;
        if (request.getRegs() != null && request.getRegs().getGdpr() != null && request.getRegs().getGdpr() == 0) {
            gdpr = 0;
        }

        final String consent = readConsent(request);

        final boolean mayForwardPersonalData = mayForwardPersonalData(request, consent);

        final BidRequest forwardableRequest = makeForwardableORTB2(request, mayForwardPersonalData);

        String currency = DEFAULT_BID_CURRENCY;
        if (request.getCur() != null && !request.getCur().isEmpty()) {
            currency = request.getCur().getFirst();
        }

        String pageUrl = "";
        if (request.getSite() != null) {
            pageUrl = request.getSite().getPage();
        }

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

        OptOutRequest data = OptOutRequest.builder()
                .publisher(publisherId)
                .slots(slots)
                .cur(CurrencySettings.builder()
                        .adServerCurrency(currency)
                        .granularityMultiplier(1)
                        .build())
                .url(pageUrl)
                .sdk_version("prebid-server")
                .gdpr_consent(consent)
                .gdpr(gdpr)
                .customs(requestCustoms)
                .build();

        if (forwardableRequest.getDevice() != null) {
            data = data.toBuilder()
                    .os(forwardableRequest.getDevice().getOs())
                    .build();
        }

        if (mayForwardPersonalData && forwardableRequest.getUser() != null
                && StringUtils.isNotBlank(forwardableRequest.getUser().getBuyeruid())) {
            data = data.toBuilder()
                    .viewer(Viewer.builder()
                            .id(forwardableRequest.getUser().getBuyeruid())
                            .matcher(MATCHER_NAME)
                            .build())
                    .build();
        }

        if (includeOrtb2) {
            data = data.toBuilder()
                    .ortb2(mapper.encodeToString(forwardableRequest))
                    .build();
        }

        final byte[] body = mapper.encodeToBytes(data);

        return Result.of(Collections.singletonList(makeHttpRequest(request, body, mayForwardPersonalData)), errors);
    }

    private ExtImpOptout parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private List<AdSize> getAllowedSizes(Imp imp) {
        final Banner banner = imp.getBanner();
        final Set<AdSize> uniqueSizes = new LinkedHashSet<>();

        if (banner.getW() != null && banner.getH() != null) {
            uniqueSizes.add(new AdSize(banner.getW(), banner.getH()));
        }

        if (banner.getFormat() != null) {
            for (Format format : banner.getFormat()) {
                if (format != null && format.getW() != null && format.getH() != null) {
                    uniqueSizes.add(new AdSize(format.getW(), format.getH()));
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
        if (customs.isEmpty()) {
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

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request, byte[] body, boolean mayForwardPersonalData) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(this.endpointUrl.toString())
                .impIds(BidderUtil.impIds(request))
                .headers(makeHeaders(request, mayForwardPersonalData))
                .body(body)
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
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final Sent sent = mapper.decodeValue(httpCall.getRequest().getBody(), Sent.class);
            final List<BidderError> errors = new ArrayList<>();
            if (sent.slots().isEmpty()) {
                return Result.withError(BidderError.badServerResponse("optout: outgoing request has no slots"));
            }

            return Result.withValues(List.of());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }
}

record AdSize(Integer w, Integer h) {
}

@Builder
record OptoutSlot(String adSlot, String id, List<AdSize> sizes, Map<String, String> customs) {
}

record Wrapped(Map<String, Object> data) {

}

@Builder(toBuilder = true)
record OptOutRequest(String publisher,
                     List<OptoutSlot> slots,
                     CurrencySettings cur,
                     String url,
                     String sdk_version,
                     String gdpr_consent,
                     int gdpr,
                     String os,
                     Viewer viewer,
                     Map<String, String> customs,
                     String ortb2) {
}

@Builder(toBuilder = true)
record CurrencySettings(String adServerCurrency, int granularityMultiplier) {

}

@Builder(toBuilder = true)
record Viewer(String id, String matcher) {

}

@Builder
record Sent(List<OptoutSlot> slots) {

}
