package org.prebid.server.bidder.adgeneration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.tuple.Pair;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.adgeneration.model.request.AdgenerationRequest;
import org.prebid.server.bidder.adgeneration.model.response.AdgenerationLocationParams;
import org.prebid.server.bidder.adgeneration.model.response.AdgenerationResponse;
import org.prebid.server.bidder.adgeneration.model.response.AdgenerationResult;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.proto.openrtb.ext.request.adgeneration.ExtImpAdgeneration;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;
import org.prebid.server.util.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AdgenerationBidder implements Bidder<AdgenerationRequest> {

    private static final String ADAPTER_VERSION = "1.6.6";
    private static final String DEFAULT_CURRENCY = "JPY";
    private static final String SDK_NAME = "prebidserver";
    private static final String POSALL_DEFAULT = "SSPLOC";

    private static final Pattern APPEND_CHILD_TO_BODY_PATTERN = Pattern.compile("</\\s?body>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BODY_PATTERN = Pattern.compile("<body>(.*)</\\s?body>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final TypeReference<ExtPrebid<?, ExtImpAdgeneration>> ADGENERATION_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final Uri endpoint;
    private final JacksonMapper mapper;
    private final String pbsVersion;

    public AdgenerationBidder(String endpointUrl,
                              String versionInfo,
                              JacksonMapper mapper) {

        this.endpoint = Uri.of(endpointUrl);
        this.mapper = Objects.requireNonNull(mapper);
        this.pbsVersion = StringUtils.defaultIfBlank(versionInfo, "unknown");
    }

    @Override
    public Result<List<HttpRequest<AdgenerationRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<AdgenerationRequest>> requests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();
        final String currency = getCurrency(request.getCur());
        final String sdkType = detectSdkType(request);
        final MultiMap headers = resolveHeaders(request.getDevice());

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpAdgeneration ext = parseImpExt(imp);
                validateImpExt(ext);

                final String uri = getUri(ext.getId(), sdkType);
                final AdgenerationRequest payload = makePayload(request, imp, currency);

                requests.add(createSingleRequest(uri, payload, headers));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(requests, errors);
    }

    private static String getCurrency(List<String> currencies) {
        return CollectionUtils.isNotEmpty(currencies)
                && currencies.stream().anyMatch("USD"::equalsIgnoreCase)
                ? "USD"
                : DEFAULT_CURRENCY;
    }

    private static String detectSdkType(BidRequest request) {
        if (!isAppContext(request)) {
            return "0";
        }
        final String os = ObjectUtil.getIfNotNull(request.getDevice(), Device::getOs);

        if (Strings.CI.equals(os, "android")) {
            return "1";
        }
        if (Strings.CI.equals(os, "ios")) {
            return "2";
        }
        return "0";
    }

    private static boolean isAppContext(BidRequest request) {
        final String channel = Optional.ofNullable(request.getExt())
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getChannel)
                .map(ExtRequestPrebidChannel::getName)
                .orElse(null);

        return StringUtils.isNotEmpty(channel)
                ? Strings.CI.equals("app", channel)
                : request.getApp() != null;
    }

    private static MultiMap resolveHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers();

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
        }

        return headers;
    }

    private ExtImpAdgeneration parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ADGENERATION_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private static void validateImpExt(ExtImpAdgeneration ext) {
        if (StringUtils.isBlank(ext.getId())) {
            throw new PreBidException("No Location ID in ExtImpAdgeneration.");
        }
    }

    private String getUri(String id, String sdkType) {
        return endpoint
                .addQueryParam("id", id)
                .addQueryParam("posall", POSALL_DEFAULT)
                .addQueryParam("sdktype", sdkType)
                .expand();
    }

    private AdgenerationRequest makePayload(BidRequest request,
                                            Imp imp,
                                            String currency) {

        final BidRequest modifiedRequest = request.toBuilder()
                .imp(Collections.singletonList(imp))
                .build();

        return AdgenerationRequest.builder()
                .currency(currency)
                .pbVersion(pbsVersion)
                .sdkName(SDK_NAME)
                .adapterVersion(ADAPTER_VERSION)
                .ortb(modifiedRequest)
                .iMark(imp.getXNative() == null ? 1 : null)
                .build();
    }

    private HttpRequest<AdgenerationRequest> createSingleRequest(String uri,
                                                                 AdgenerationRequest payload,
                                                                 MultiMap headers) {

        return HttpRequest.<AdgenerationRequest>builder()
                .method(HttpMethod.POST)
                .uri(uri)
                .headers(MultiMap.caseInsensitiveMultiMap().addAll(headers))
                .impIds(Set.of(payload.getOrtb().getImp().getFirst().getId()))
                .body(mapper.encodeToBytes(payload))
                .payload(payload)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<AdgenerationRequest> httpCall, BidRequest bidRequest) {
        try {
            final AdgenerationResponse response = mapper.decodeValue(
                    httpCall.getResponse().getBody(), AdgenerationResponse.class);
            return extractBid(response, httpCall.getRequest().getPayload(), bidRequest);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private Result<List<BidderBid>> extractBid(AdgenerationResponse response,
                                               AdgenerationRequest requestPayload,
                                               BidRequest bidRequest) {

        if (response == null || CollectionUtils.isEmpty(response.getResults())) {
            return Result.empty();
        }

        final Imp requestedImp = requestPayload.getOrtb().getImp().getFirst();
        final Imp imp = findMatchingImp(requestedImp.getId(), bidRequest.getImp());
        if (imp == null) {
            return Result.empty();
        }

        try {
            return Result.withValue(makeBidderBid(response, imp, bidRequest.getId(), getCurrency(bidRequest.getCur())));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static Imp findMatchingImp(String impId, List<Imp> imps) {
        return imps.stream()
                .filter(imp -> Objects.equals(imp.getId(), impId))
                .findFirst()
                .orElse(null);
    }

    private BidderBid makeBidderBid(AdgenerationResponse response, Imp imp, String requestId, String currency) {
        final AdgenerationResult result = response.getResults().getFirst();
        final Pair<BidType, String> markup = resolveAdMarkup(result, response.getLocationParams(), imp);
        final Bid bid = makeBid(result, requestId, imp.getId(), markup.getRight());

        return BidderBid.of(bid, markup.getLeft(), currency);
    }

    private static Bid makeBid(AdgenerationResult result, String requestId, String impId, String adm) {
        return Bid.builder()
                .id("%s-%s".formatted(requestId, impId))
                .impid(impId)
                .adm(adm)
                .price(result.getCpm())
                .w(result.getW())
                .h(result.getH())
                .crid(result.getCreativeId())
                .dealid(result.getDealId())
                .adomain(CollectionUtils.isEmpty(result.getAdomain()) ? null : result.getAdomain())
                .build();
    }

    private Pair<BidType, String> resolveAdMarkup(AdgenerationResult result,
                                                  AdgenerationLocationParams params,
                                                  Imp imp) {

        final ObjectNode nativeAd = imp.getXNative() != null ? resolveNativeAd(result.getXNative()) : null;
        if (nativeAd != null) {
            return Pair.of(BidType.xNative, makeNativeAdm(nativeAd, result.getBeaconUrl()));
        }

        if (imp.getBanner() != null) {
            return Pair.of(BidType.banner, makeBannerAdm(result, params, imp));
        }

        throw new PreBidException(
                "no usable native assets in the response for imp %s and the imp has no banner"
                        .formatted(imp.getId()));
    }

    private static ObjectNode resolveNativeAd(JsonNode rawNative) {
        if (!(rawNative instanceof ObjectNode)) {
            return null;
        }

        final JsonNode nativeNode = rawNative.has("native") ? rawNative.get("native") : rawNative;
        if (!(nativeNode instanceof ObjectNode nativeAd)) {
            return null;
        }

        final JsonNode assets = nativeAd.path("assets");
        return assets.isArray() && !assets.isEmpty() ? nativeAd : null;
    }

    private String makeNativeAdm(ObjectNode nativeNode, String beaconUrl) {
        final ObjectNode nativeAd = nativeNode.deepCopy();

        if (StringUtils.isNotBlank(beaconUrl)) {
            appendBeaconIfAbsent(nativeAd, beaconUrl);
        }

        final ObjectNode wrapped = mapper.mapper().createObjectNode();
        wrapped.set("native", nativeAd);

        return mapper.encodeToString(wrapped);
    }

    private static void appendBeaconIfAbsent(ObjectNode nativeAd, String beaconUrl) {
        final ArrayNode trackers = resolveImpTrackers(nativeAd);
        if (!containsBeacon(trackers, beaconUrl)) {
            trackers.add(beaconUrl);
        }
    }

    private static ArrayNode resolveImpTrackers(ObjectNode nativeAd) {
        final JsonNode trackers = nativeAd.get("imptrackers");

        if (trackers == null || trackers.isNull()) {
            return nativeAd.putArray("imptrackers");
        }
        if (trackers instanceof ArrayNode array) {
            return array;
        }

        throw new PreBidException("Invalid native imp trackers");
    }

    private static boolean containsBeacon(ArrayNode trackers, String beaconUrl) {
        boolean beaconPresent = false;
        for (JsonNode tracker : trackers) {
            if (!tracker.isTextual()) {
                throw new PreBidException("Invalid native.imptrackers");
            }
            if (beaconUrl.equals(tracker.asText())) {
                beaconPresent = true;
            }
        }

        return beaconPresent;
    }

    private String makeBannerAdm(AdgenerationResult result, AdgenerationLocationParams params, Imp imp) {
        final String vastXml = result.getVastxml();
        final String ad = StringUtils.isNotBlank(vastXml)
                ? makeVastAdm(vastXml, params, imp)
                : StringUtils.defaultString(result.getAd());
        final String adWithBeacon = appendChildToBody(ad, result.getBeacon());

        return removeWrapper(adWithBeacon);
    }

    private String makeVastAdm(String vastXml, AdgenerationLocationParams params, Imp imp) {
        final String encodedVastXml = HttpUtil.encodeUrl(vastXml);

        if (isUpperBillboard(params)) {
            final ExtImpAdgeneration ext = parseImpExt(imp);
            validateImpExt(ext);

            return wrapWithADGBrowserM(encodedVastXml, ext.getMarginTop());
        }

        return wrapWithAPV(imp.getId(), encodedVastXml);
    }

    private static boolean isUpperBillboard(AdgenerationLocationParams params) {
        return params != null
                && params.getOption() != null
                && Strings.CS.equals("upper_billboard", params.getOption().getAdType());
    }

    private static String wrapWithADGBrowserM(String encodedVastXml, String marginTop) {
        return """
                <body>
                <script type="text/javascript" src="https://i.socdm.com/sdk/js/adg-browser-m.js"></script>
                <script type="text/javascript">
                window.ADGBrowserM.init({vastXml: decodeURIComponent('%s'), marginTop: '%s'});
                </script>
                </body>""".formatted(
                encodedVastXml,
                escapeJavaScript(StringUtils.defaultIfEmpty(marginTop, "0")));
    }

    private static String wrapWithAPV(String impId, String encodedVastXml) {
        return """
                <body>
                <div id="apvad-%s"></div>
                <script type="text/javascript" id="apv" src="https://cdn.apvdr.com/js/VideoAd.min.js"></script>
                <script type="text/javascript">
                (function() {
                    new APV.VideoAd({s:"%s"}).load(decodeURIComponent('%s'));
                })();
                </script>
                </body>""".formatted(
                StringEscapeUtils.escapeHtml4(impId),
                escapeJavaScript(impId),
                encodedVastXml);
    }

    private static String escapeJavaScript(String value) {
        return StringEscapeUtils.escapeEcmaScript(value).replace("<", "\\u003C");
    }

    private static String appendChildToBody(String ad, String beacon) {
        final Matcher matcher = APPEND_CHILD_TO_BODY_PATTERN.matcher(ad);
        return matcher.find()
                ? matcher.replaceFirst(Matcher.quoteReplacement(StringUtils.defaultString(beacon) + "</body>"))
                : ad;
    }

    private static String removeWrapper(String ad) {
        final Matcher matcher = BODY_PATTERN.matcher(ad);
        return matcher.find() ? StringUtils.defaultIfEmpty(matcher.group(1).trim(), ad) : ad;
    }
}
