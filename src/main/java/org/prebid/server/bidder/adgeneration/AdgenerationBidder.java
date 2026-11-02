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
import org.prebid.server.bidder.model.HttpResponse;
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

    private static final Pattern NEWLINE_PATTERN = Pattern.compile("\\r?\\n");
    private static final Pattern APPEND_CHILD_TO_BODY_PATTERN = Pattern.compile("</\\s?body>",
            Pattern.CASE_INSENSITIVE);

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
        final String currency = getCurrency(request);
        for (Imp imp : request.getImp()) {
            try {
                requests.add(makeHttpRequest(request, imp, currency));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(requests, errors);
    }

    private static String getCurrency(BidRequest request) {
        final List<String> currencies = request.getCur();

        return CollectionUtils.isNotEmpty(currencies)
                && currencies.stream().anyMatch("USD"::equalsIgnoreCase)
                ? "USD"
                : DEFAULT_CURRENCY;
    }

    private HttpRequest<AdgenerationRequest> makeHttpRequest(BidRequest request,
                                                             Imp imp,
                                                             String currency) {

        final ExtImpAdgeneration ext = parseAndValidateImpExt(imp);
        final String uri = getUri(ext.getId(), request);
        final BidRequest modifiedRequest = request.toBuilder()
                .imp(Collections.singletonList(imp))
                .build();

        final AdgenerationRequest payload = AdgenerationRequest.builder()
                .currency(currency)
                .pbVersion(pbsVersion)
                .sdkName(SDK_NAME)
                .adapterVersion(ADAPTER_VERSION)
                .ortb(modifiedRequest)
                .iMark(imp.getXNative() == null ? 1 : null)
                .build();

        return createSingleRequest(uri, payload, imp.getId());
    }

    private ExtImpAdgeneration parseAndValidateImpExt(Imp imp) {
        final ExtImpAdgeneration extImpAdgeneration;
        try {
            extImpAdgeneration = mapper.mapper().convertValue(imp.getExt(), ADGENERATION_EXT_TYPE_REFERENCE)
                    .getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }

        if (StringUtils.isBlank(extImpAdgeneration.getId())) {
            throw new PreBidException("No Location ID in ExtImpAdgeneration.");
        }
        return extImpAdgeneration;
    }

    private String getUri(String id, BidRequest request) {
        return endpoint
                .addQueryParam("id", id)
                .addQueryParam("posall", POSALL_DEFAULT)
                .addQueryParam("sdktype", detectSdkType(request))
                .expand();
    }

    private static String detectSdkType(BidRequest request) {
        if (!isAppContext(request)) {
            return "0";
        }
        final String os = ObjectUtil.getIfNotNull(request.getDevice(), Device::getOs);

        if (Strings.CI.equals(os, "android")) {
            return "1";
        } else if (Strings.CI.equals(os, "ios")) {
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

    private HttpRequest<AdgenerationRequest> createSingleRequest(String uri,
                                                                 AdgenerationRequest request,
                                                                 String impId) {

        return HttpRequest.<AdgenerationRequest>builder()
                .method(HttpMethod.POST)
                .uri(uri)
                .headers(resolveHeaders(request.getOrtb().getDevice()))
                .impIds(Set.of(impId))
                .body(mapper.encodeToBytes(request))
                .payload(request)
                .build();
    }

    private static MultiMap resolveHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers();

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
        }

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<AdgenerationRequest> httpCall, BidRequest bidRequest) {
        try {
            final AdgenerationResponse response = decodeBodyToBidResponse(httpCall.getResponse());
            if (response == null || CollectionUtils.isEmpty(response.getResults())) {
                return Result.empty();
            }

            final Imp imp = findMatchingImp(httpCall.getRequest().getPayload(), bidRequest);
            if (imp == null) {
                return Result.empty();
            }

            return Result.withValue(makeBidderBid(response, bidRequest, imp));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private AdgenerationResponse decodeBodyToBidResponse(HttpResponse httpResponse) {
        try {
            return mapper.decodeValue(httpResponse.getBody(), AdgenerationResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static Imp findMatchingImp(AdgenerationRequest adgenerationRequest, BidRequest request) {
        final String impId = adgenerationRequest
                .getOrtb()
                .getImp()
                .getFirst()
                .getId();

        return request.getImp().stream()
                .filter(imp -> Objects.equals(imp.getId(), impId))
                .findFirst()
                .orElse(null);
    }

    private BidderBid makeBidderBid(AdgenerationResponse response, BidRequest request, Imp imp) {
        final AdgenerationResult result = response.getResults().getFirst();
        final Pair<BidType, String> markup = buildAdMarkup(result, response.getLocationParams(), imp);

        final Bid bid = Bid.builder()
                .id("%s-%s".formatted(request.getId(), imp.getId()))
                .impid(imp.getId())
                .adm(markup.getRight())
                .price(result.getCpm())
                .w(result.getW())
                .h(result.getH())
                .crid(result.getCreativeId())
                .dealid(result.getDealId())
                .adomain(CollectionUtils.isEmpty(result.getAdomain()) ? null : result.getAdomain())
                .build();

        return BidderBid.of(bid, markup.getLeft(), getCurrency(request));
    }

    private Pair<BidType, String> buildAdMarkup(
            AdgenerationResult result,
            AdgenerationLocationParams params,
            Imp imp) {

        if (imp.getXNative() != null && hasNativeAssets(result.getXNative())) {
            return Pair.of(BidType.xNative, wrapNativeAdm(result.getXNative(), result.getBeaconUrl()));
        }

        if (imp.getBanner() == null) {
            throw new PreBidException(
                    "no usable native assets in the response for imp %s and the imp has no banner"
                            .formatted(imp.getId()));
        }

        return Pair.of(BidType.banner, getAdm(result, params, imp));
    }

    private static boolean hasNativeAssets(JsonNode rawNative) {
        if (rawNative == null || !rawNative.isObject()) {
            return false;
        }

        final JsonNode nativeNode = rawNative.get("native");
        final JsonNode targetNode = nativeNode != null ? nativeNode : rawNative;
        final JsonNode assets = targetNode.path("assets");

        return assets.isArray() && !assets.isEmpty();
    }

    private String wrapNativeAdm(JsonNode raw, String beaconUrl) {
        final JsonNode nativeNode = raw.has("native") ? raw.get("native") : raw;
        final ObjectNode nativeAd = nativeNode.deepCopy();

        if (StringUtils.isNotBlank(beaconUrl)) {
            appendBeaconIfAbsent(nativeAd, beaconUrl);
        }

        final ObjectNode wrapped = mapper.mapper().createObjectNode();
        wrapped.set("native", nativeAd);

        return mapper.encodeToString(wrapped);
    }

    private void appendBeaconIfAbsent(ObjectNode nativeAd, String beaconUrl) {
        final JsonNode impTrackers = nativeAd.get("imptrackers");
        if (impTrackers != null && !impTrackers.isNull() && !impTrackers.isArray()) {
            throw new PreBidException("Invalid native imp trackers");
        }

        final ArrayNode trackers = impTrackers != null && impTrackers.isArray()
                ? (ArrayNode) impTrackers
                : mapper.mapper().createArrayNode();

        boolean contains = false;
        for (JsonNode tracker : trackers) {
            if (!tracker.isTextual()) {
                throw new PreBidException("Invalid native.imptrackers");
            }
            if (beaconUrl.equals(tracker.asText())) {
                contains = true;
            }
        }

        if (!contains) {
            trackers.add(beaconUrl);
            nativeAd.set("imptrackers", trackers);
        }
    }

    private String getAdm(AdgenerationResult result, AdgenerationLocationParams params, Imp imp) {
        String ad = StringUtils.defaultString(result.getAd());
        final String vastXml = result.getVastxml();

        if (StringUtils.isNotBlank(vastXml)) {
            final String encodedVastXml = HttpUtil.encodeUrl(
                    NEWLINE_PATTERN.matcher(vastXml).replaceAll(""));

            ad = isUpperBillboard(params)
                    ? wrapWithADGBrowserM(encodedVastXml, parseAndValidateImpExt(imp).getMarginTop())
                    : wrapWithAPV(imp.getId(), encodedVastXml);
        }

        ad = appendChildToBody(ad, result.getBeacon());

        final String unwrappedAd = removeWrapper(ad);
        return StringUtils.isNotEmpty(unwrappedAd) ? unwrappedAd : ad;
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
                StringEscapeUtils.escapeEcmaScript(StringUtils.defaultIfEmpty(marginTop, "0"))
                        .replace("<", "\\u003C"));
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
                StringEscapeUtils.escapeEcmaScript(impId).replace("<", "\\u003C"),
                encodedVastXml);
    }

    private static String appendChildToBody(String ad, String beacon) {
        final Matcher matcher = APPEND_CHILD_TO_BODY_PATTERN.matcher(ad);
        return matcher.find()
                ? matcher.replaceFirst(Matcher.quoteReplacement(StringUtils.defaultString(beacon) + "</body>"))
                : ad;
    }

    private static String removeWrapper(String ad) {
        final int start = ad.indexOf("<body>");
        final int end = ad.lastIndexOf("</body>");
        return start == -1 || end == -1 || start >= end
                ? ""
                : ad.substring(start + "<body>".length(), end).trim();
    }
}
