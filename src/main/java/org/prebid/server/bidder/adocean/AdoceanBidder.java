package org.prebid.server.bidder.adocean;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.uritemplate.UriTemplate;
import io.vertx.uritemplate.Variables;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.adocean.model.AdoceanResponseAdUnit;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.adocean.ExtImpAdocean;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.UriTemplateUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class AdoceanBidder implements Bidder<Void> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdocean>> ADOCEAN_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String VERSION = "1.3.0";
    private static final int MAX_URI_LENGTH = 8000;
    private static final String MEASUREMENT_CODE_TEMPLATE = """
             <script> +function() {
            var wu = "%s";
            var su = "%s".replace(/\\[TIMESTAMP\\]/, Date.now());
            if (wu && !(navigator.sendBeacon && navigator.sendBeacon(wu))) { (new Image(1,1)).src = wu }
            if (su && !(navigator.sendBeacon && navigator.sendBeacon(su))) { (new Image(1,1)).src = su } }();
            </script>""";

    private static final String HOST_MACRO_NAME = "Host";

    private final JacksonMapper mapper;
    private final UriTemplate uriTemplate;

    public AdoceanBidder(String endpointUrl, JacksonMapper mapper) {
        HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.uriTemplate = UriTemplateUtil.createTemplate(
                "%s/_%s/ad.json".formatted(endpointUrl, "{randomizedPart}"),
                List.of(HOST_MACRO_NAME));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<Void>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final User user = request.getUser();
        final ExtUser extUser = user != null ? user.getExt() : null;
        final String consentString = extUser != null ? extUser.getConsent() : "";
        final Map<HttpRequest<Void>, HttpRequestWrapper> httpRequestWrapperMap = new HashMap<>();

        final List<HttpRequest<Void>> httpRequests = new ArrayList<>();
        for (Imp imp : request.getImp()) {
            try {
                final ExtImpAdocean extImpAdocean = parseImpExt(imp);
                validateImpExt(extImpAdocean);

                final Map<String, String> slaveSizes = new HashMap<>();
                slaveSizes.put(extImpAdocean.getSlaveId(), getImpSizes(imp));
                if (addRequestAndCheckIfDuplicates(
                        httpRequests,
                        extImpAdocean,
                        imp.getId(),
                        slaveSizes,
                        request.getTest(),
                        httpRequestWrapperMap)) {
                    continue;
                }
                httpRequests.add(createSingleRequest(
                        request,
                        imp,
                        extImpAdocean,
                        consentString,
                        slaveSizes,
                        httpRequestWrapperMap));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(httpRequests, errors);
    }

    private ExtImpAdocean parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ADOCEAN_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(
                    "Error parsing adOceanExt parameters, in imp with id : " + imp.getId());
        }
    }

    private static void validateImpExt(ExtImpAdocean impExt) {
        if (StringUtils.isEmpty(impExt.getEmitterPrefix())) {
            throw new PreBidException("No emitterPrefix param");
        }
    }

    private boolean addRequestAndCheckIfDuplicates(List<HttpRequest<Void>> httpRequests,
                                                   ExtImpAdocean extImpAdocean,
                                                   String impid,
                                                   Map<String, String> slaveSizes,
                                                   Integer test,
                                                   Map<HttpRequest<Void>, HttpRequestWrapper> httpRequestWrapperMap) {

        for (HttpRequest<Void> request : httpRequests) {
            final HttpRequestWrapper httpRequestWrapper = httpRequestWrapperMap.get(request);
            final Map<String, String> queryParams = new HashMap<>(httpRequestWrapper.queryParams());
            final String masterId = queryParams.get("id");

            if (masterId != null && masterId.equals(extImpAdocean.getMasterId())) {
                final boolean isExistingSlaveId = Objects.equals(
                        queryParams.get("aid").split(":")[0],
                        extImpAdocean.getSlaveId());

                if (isExistingSlaveId) {
                    continue;
                }

                queryParams.put("aid", extImpAdocean.getSlaveId() + ":" + impid);
                final List<String> sizeValues = setSlaveSizesParam(slaveSizes, Objects.equals(test, 1));
                if (CollectionUtils.isNotEmpty(sizeValues)) {
                    queryParams.put("aosspsizes", String.join("-", sizeValues));
                }

                final String finalUrl = UriTemplateUtil.createTemplate(
                                httpRequestWrapper.url(),
                                false,
                                "queryParams")
                        .expandToString(Variables.variables()
                                .set("url", httpRequestWrapper.url())
                                .set("queryParams", queryParams));

                if (finalUrl.length() < MAX_URI_LENGTH) {
                    final HttpRequest<Void> updatedRequest = HttpRequest.<Void>builder()
                            .method(HttpMethod.GET)
                            .uri(finalUrl)
                            .headers(request.getHeaders())
                            .build();
                    httpRequests.remove(request);
                    httpRequests.add(updatedRequest);
                    return true;
                }
            }
        }
        return false;
    }

    private String getImpSizes(Imp imp) {
        final Banner banner = imp.getBanner();
        if (banner == null) {
            return "";
        }

        final List<Format> format = banner.getFormat();
        if (CollectionUtils.isNotEmpty(format)) {
            final List<String> sizes = new ArrayList<>();
            format.forEach(singleFormat -> sizes.add(
                    "%sx%s".formatted(getIntOrElseZero(singleFormat.getW()), getIntOrElseZero(singleFormat.getH()))));
            return String.join("_", sizes);
        }

        final Integer w = banner.getW();
        final Integer h = banner.getH();
        if (w != null && h != null) {
            return "%sx%s".formatted(w, h);
        }

        return StringUtils.EMPTY;
    }

    private int getIntOrElseZero(Integer number) {
        return number != null ? number : 0;
    }

    private HttpRequest<Void> createSingleRequest(BidRequest request,
                                                  Imp imp,
                                                  ExtImpAdocean extImpAdocean,
                                                  String consentString,
                                                  Map<String, String> slaveSizes,
                                                  Map<HttpRequest<Void>, HttpRequestWrapper> httpRequestWrapperMap) {

        final HttpRequestWrapper httpRequestWrapper = buildHttpRequestWrapper(
                imp.getId(), extImpAdocean, consentString, request, slaveSizes);

        final HttpRequest<Void> httpRequest = HttpRequest.<Void>builder()
                .method(HttpMethod.GET)
                .uri(httpRequestWrapper.url() + httpRequestWrapper.query())
                .headers(getHeaders(request))
                .build();

        httpRequestWrapperMap.put(httpRequest, httpRequestWrapper);
        return httpRequest;
    }

    private HttpRequestWrapper buildHttpRequestWrapper(String impId,
                                                       ExtImpAdocean extImpAdocean,
                                                       String consentString,
                                                       BidRequest bidRequest,
                                                       Map<String, String> slaveSizes) {

        final Map<String, String> queryParams = new HashMap<>();

        queryParams.put("pbsrv_v", VERSION);
        queryParams.put("id", extImpAdocean.getMasterId());
        queryParams.put("nc", "1");
        queryParams.put("nosecure", "1");
        queryParams.put("aid", extImpAdocean.getSlaveId() + ":" + impId);

        if (StringUtils.isNotEmpty(consentString)) {
            queryParams.put("gdpr_consent", consentString);
            queryParams.put("gdpr", "1");
        }

        final User user = bidRequest.getUser();
        if (user != null && StringUtils.isNotEmpty(user.getBuyeruid())) {
            queryParams.put("hcuserid", user.getBuyeruid());
        }

        final App app = bidRequest.getApp();
        if (app != null) {
            addParameterIfNotEmpty(queryParams, "app", "1");
            addParameterIfNotEmpty(queryParams, "appname", app.getName());
            addParameterIfNotEmpty(queryParams, "appbundle", app.getBundle());
            addParameterIfNotEmpty(queryParams, "appdomain", app.getDomain());
        }

        final Device device = bidRequest.getDevice();
        if (device != null) {
            if (StringUtils.isNotEmpty(device.getIfa())) {
                queryParams.put("ifa", device.getIfa());
            } else if (StringUtils.isNotEmpty(device.getDpidmd5())) {
                queryParams.put("dpidmd5", device.getDpidmd5());
            }

            addParameterIfNotEmpty(queryParams, "devos", device.getOs());
            addParameterIfNotEmpty(queryParams, "devosv", device.getOsv());
            addParameterIfNotEmpty(queryParams, "devmodel", device.getModel());
            addParameterIfNotEmpty(queryParams, "devmake", device.getMake());
        }

        final Integer test = bidRequest.getTest();
        final List<String> sizeValues = setSlaveSizesParam(slaveSizes, Objects.equals(test, 1));

        if (CollectionUtils.isNotEmpty(sizeValues)) {
            queryParams.put("aosspsizes", String.join("-", sizeValues));
        }

        final int randomizedPart = Objects.equals(test, 1) ? 10000000 : 10000000 + (int) (Math.random() * 89999999);
        final String resolvedUrl = uriTemplate.expandToString(Variables.variables()
                .set(HOST_MACRO_NAME, extImpAdocean.getEmitterPrefix())
                .set("randomizedPart", String.valueOf(randomizedPart)));

        final String urlQuery = UriTemplateUtil.ONLY_QUERY_URI_TEMPLATE.expandToString(Variables.variables()
                .set("queryParams", queryParams));

        return new HttpRequestWrapper(resolvedUrl, urlQuery, queryParams);
    }

    private static void addParameterIfNotEmpty(Map<String, String> queryParams, String parameter, String value) {
        if (StringUtils.isNotEmpty(value)) {
            queryParams.put(parameter, value);
        }
    }

    private List<String> setSlaveSizesParam(Map<String, String> slaveSizes, boolean orderByKey) {
        final Set<String> slaveIDs = orderByKey ? new TreeSet<>(slaveSizes.keySet()) : slaveSizes.keySet();

        return slaveIDs.stream()
                .filter(slaveId -> StringUtils.isNotEmpty(slaveSizes.get(slaveId)))
                .map(rawSlaveID -> "%s~%s".formatted(
                        rawSlaveID.replaceFirst("adocean", ""),
                        slaveSizes.get(rawSlaveID)))
                .toList();
    }

    private static MultiMap getHeaders(BidRequest request) {
        final MultiMap headers = HttpUtil.headers();

        final Device device = request.getDevice();
        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIpv6());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
        }

        final Site site = request.getSite();
        if (site != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.REFERER_HEADER, site.getPage());
        }

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<Void> httpCall, BidRequest bidRequest) {
        final Map<String, List<String>> params;
        try {
            params = HttpUtil.parseQuery(HttpUtil.parseUrl(httpCall.getRequest().getUri()).getQuery());
        } catch (MalformedURLException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final Map<String, String> auctionIds = params != null ? params.get("aid").stream()
                .map(value -> value.split(":"))
                .collect(Collectors.toMap(name -> name[0], value -> value[1])) : null;

        final List<AdoceanResponseAdUnit> adoceanResponses;
        try {
            adoceanResponses = getAdoceanResponseAdUnitList(httpCall.getResponse().getBody());
        } catch (PreBidException e) {
            return Result.withError(BidderError
                    .badServerResponse("Failed to decode: No content to map due to end-of-input"));
        }

        final List<BidderBid> bidderBids = adoceanResponses.stream()
                .filter(adoceanResponse -> !"true".equals(adoceanResponse.getError()))
                .filter(adoceanResponse ->
                        StringUtils.isNotBlank(MapUtils.getString(auctionIds, adoceanResponse.getId())))
                .map(adoceanResponse -> BidderBid.of(createBid(auctionIds, adoceanResponse), BidType.banner,
                        adoceanResponse.getCurrency()))
                .toList();

        return Result.withValues(bidderBids);
    }

    private static Bid createBid(Map<String, String> auctionIds, AdoceanResponseAdUnit adoceanResponse) {
        final String adm = MEASUREMENT_CODE_TEMPLATE
                .formatted(adoceanResponse.getWinUrl(), adoceanResponse.getStatsUrl())
                + HttpUtil.decodeUrl(adoceanResponse.getCode());
        final String bidPrice = adoceanResponse.getPrice();

        return Bid.builder()
                .id(adoceanResponse.getId())
                .impid(auctionIds.get(adoceanResponse.getId()))
                .adm(adm)
                .price(NumberUtils.isParsable(bidPrice) ? new BigDecimal(bidPrice) : BigDecimal.ZERO)
                .w(NumberUtils.toInt(adoceanResponse.getWidth(), 0))
                .h(NumberUtils.toInt(adoceanResponse.getHeight(), 0))
                .crid(adoceanResponse.getCrid())
                .build();
    }

    private List<AdoceanResponseAdUnit> getAdoceanResponseAdUnitList(String responseBody) {
        try {
            return mapper.mapper().readValue(
                    responseBody,
                    mapper.mapper().getTypeFactory().constructCollectionType(List.class, AdoceanResponseAdUnit.class));
        } catch (IOException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private record HttpRequestWrapper(String url, String query, Map<String, String> queryParams) {

    }
}
