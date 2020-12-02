package org.prebid.server.bidder.adocean;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.adocean.model.AdoceanResponseAdUnit;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.adocean.ExtImpAdocean;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Adocean {@link Bidder} implementation.
 */
public class AdoceanBidder implements Bidder<Void> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdocean>> ADOCEAN_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAdocean>>() {
            };
    private static final String VERSION = "1.1.0";
    private static final int MAX_URI_LENGTH = 8000;
    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final String MEASUREMENT_CODE_TEMPLATE = " <script> +function() { "
            + "var wu = \"%s\"; "
            + "var su = \"%s\".replace(/\\[TIMESTAMP\\]/, Date.now()); "
            + "if (wu && !(navigator.sendBeacon && navigator.sendBeacon(wu))) { (new Image(1,1)).src = wu } "
            + "if (su && !(navigator.sendBeacon && navigator.sendBeacon(su))) { (new Image(1,1)).src = su } }(); "
            + "</script> ";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AdoceanBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<Void>>> makeHttpRequests(BidRequest request) {
        final User user = request.getUser();
        final ExtUser extUser = user != null ? user.getExt() : null;
        final String consent = extUser != null ? extUser.getConsent() : null;
        final String consentString = StringUtils.isNotBlank(consent) ? consent : "";

        final List<HttpRequest<Void>> httpRequests = new ArrayList<>();
        for (Imp imp : request.getImp()) {
            try {
                final ExtImpAdocean extImpAdocean = parseImpExt(imp);
                final Map<String, String> slaveSizes = new HashMap<>();
                slaveSizes.put(extImpAdocean.getSlaveId(), getImpSizes(imp));
                if (addRequestAndCheckIfDuplicates(httpRequests, extImpAdocean, imp.getId(), slaveSizes,
                        request.getTest())) {
                    continue;
                }
                httpRequests.add(createSingleRequest(request, imp, extImpAdocean, consentString, slaveSizes));
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.withValues(httpRequests);
    }

    private ExtImpAdocean parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ADOCEAN_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private boolean addRequestAndCheckIfDuplicates(List<HttpRequest<Void>> httpRequests, ExtImpAdocean extImpAdocean,
                                                   String impid, Map<String, String> slaveSizes, Integer test) {
        for (HttpRequest<Void> request : httpRequests) {
            try {
                final URIBuilder uriBuilder = new URIBuilder(request.getUri());
                final List<NameValuePair> queryParams = uriBuilder.getQueryParams();

                final String masterId = queryParams.stream()
                        .filter(param -> param.getName().equals("id"))
                        .findFirst()
                        .map(NameValuePair::getValue)
                        .orElse(null);

                if (masterId != null && masterId.equals(extImpAdocean.getMasterId())) {
                    final String newSlaveId = queryParams.stream()
                            .filter(param -> param.getName().equals("aid"))
                            .map(param -> param.getValue().split(":")[0])
                            .filter(slaveId -> slaveId.equals(extImpAdocean.getSlaveId()))
                            .findFirst()
                            .orElse(null);
                    if (StringUtils.isNotBlank(newSlaveId)) {
                        continue;
                    }

                    queryParams.add(new BasicNameValuePair("aid", extImpAdocean.getSlaveId() + ":" + impid));
                    final List<String> sizeValues = test != null
                            ? setSlaveSizesParam(slaveSizes, test == 1)
                            : setSlaveSizesParam(slaveSizes, false);
                    if (CollectionUtils.isNotEmpty(sizeValues)) {
                        queryParams.add(new BasicNameValuePair("aosspsizes", String.join("-", sizeValues)));
                    }

                    final String url = HttpUtil.encodeUrl(String.valueOf(queryParams));
                    if (url.length() < MAX_URI_LENGTH) {
                        return true;
                    }
                }
            } catch (URISyntaxException e) {
                throw new PreBidException(e.getMessage());
            }
        }
        return false;
    }

    private HttpRequest<Void> createSingleRequest(BidRequest request, Imp imp, ExtImpAdocean extImpAdocean,
                                                  String consentString, Map<String, String> slaveSizes) {

        return HttpRequest.<Void>builder()
                .method(HttpMethod.GET)
                .uri(buildUrl(imp.getId(), extImpAdocean, consentString, request.getTest(), request.getUser(),
                        slaveSizes))
                .headers(getHeaders(request))
                .build();
    }

    private String getImpSizes(Imp imp) {
        final Banner banner = imp.getBanner();
        if (banner == null) {
            return "";
        }

        final List<Format> format = banner.getFormat();
        if (CollectionUtils.isNotEmpty(format)) {
            final List<String> sizes = new ArrayList<>();
            format.forEach(singleFormat -> sizes.add(String.format("%sx%s", singleFormat.getW(), singleFormat.getH())));
            return String.join("_", sizes);
        }

        final Integer w = banner.getW();
        final Integer h = banner.getH();
        if (w != null && h != null) {
            return String.format("%sx%s", w, h);
        }

        return "";
    }

    private String buildUrl(String impid, ExtImpAdocean extImpAdocean, String consentString, Integer test, User user,
                            Map<String, String> slaveSizes) {
        final String url = endpointUrl.replace("{{Host}}", extImpAdocean.getEmitterDomain());
        final int randomizedPart = test != null && test == 1 ? 10000000 : 10000000 + (int) (Math.random() * 89999999);
        final String updateUrl = String.format("%s/_%s/ad.json", url, randomizedPart);
        final URIBuilder uriBuilder = new URIBuilder()
                .setPath(updateUrl)
                .addParameter("pbsrv_v", VERSION)
                .addParameter("id", extImpAdocean.getMasterId())
                .addParameter("nc", "1")
                .addParameter("nosecure", "1")
                .addParameter("aid", extImpAdocean.getSlaveId() + ":" + impid);

        if (StringUtils.isNotBlank(consentString)) {
            uriBuilder.addParameter("gdpr_consent", consentString);
            uriBuilder.addParameter("gdpr", "1");
        }

        if (user != null && StringUtils.isNotBlank(user.getBuyeruid())) {
            uriBuilder.addParameter("hcuserid", user.getBuyeruid());
        }

        final List<String> sizeValues = test != null
                ? setSlaveSizesParam(slaveSizes, test == 1)
                : setSlaveSizesParam(slaveSizes, false);

        if (CollectionUtils.isNotEmpty(sizeValues)) {
            uriBuilder.addParameter("aosspsizes", String.join("-", sizeValues));
        }

        return uriBuilder.toString();
    }

    private List<String> setSlaveSizesParam(Map<String, String> slaveSizes, boolean orderByKey) {
        final Set<String> slaveIDs = orderByKey ? new TreeSet<>(slaveSizes.keySet()) : slaveSizes.keySet();

        return slaveIDs.stream()
                .filter(slaveId -> StringUtils.isNotBlank(slaveSizes.get(slaveId)))
                .map(rawSlaveID -> String.format("%s~%s", rawSlaveID.replaceFirst("adocean", ""),
                        slaveSizes.get(rawSlaveID)))
                .collect(Collectors.toList());
    }

    private static MultiMap getHeaders(BidRequest request) {
        final MultiMap headers = HttpUtil.headers();

        final Device device = request.getDevice();
        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIpv6());
        }

        final Site site = request.getSite();
        if (site != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.REFERER_HEADER, site.getPage());
        }

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<Void> httpCall, BidRequest bidRequest) {
        final List<NameValuePair> params;
        try {
            params = URLEncodedUtils.parse(new URI(httpCall.getRequest().getUri()), StandardCharsets.UTF_8);
        } catch (URISyntaxException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final Map<String, String> auctionIds = params != null ? params.stream()
                .filter(param -> param.getName().equals("aid"))
                .map(param -> param.getValue().split(":"))
                .collect(Collectors.toMap(name -> name[0], value -> value[1])) : null;

        final List<AdoceanResponseAdUnit> adoceanResponses;
        try {
            adoceanResponses = getAdoceanResponseAdUnitList(httpCall.getResponse().getBody());
        } catch (PreBidException e) {
            return Result.withError(BidderError
                    .badServerResponse("Failed to decode: No content to map due to end-of-input"));
        }

        final List<BidderBid> bidderBids = adoceanResponses.stream()
                .filter(adoceanResponse -> !adoceanResponse.getError().equals("true"))
                .filter(adoceanResponse -> auctionIds != null
                        && StringUtils.isNotBlank(auctionIds.get(adoceanResponse.getId())))
                .map(adoceanResponse -> BidderBid.of(createBid(auctionIds, adoceanResponse), BidType.banner,
                        getBidCurrency(adoceanResponse)))
                .collect(Collectors.toList());

        return Result.withValues(bidderBids);
    }

    private static Bid createBid(Map<String, String> auctionIds, AdoceanResponseAdUnit adoceanResponse) {
        final String adm = String.format(MEASUREMENT_CODE_TEMPLATE, adoceanResponse.getWinUrl(),
                adoceanResponse.getStatsUrl()) + HttpUtil.decodeUrl(adoceanResponse.getCode());
        return Bid.builder()
                .id(adoceanResponse.getId())
                .impid(auctionIds.get(adoceanResponse.getId()))
                .adm(adm)
                .price(new BigDecimal(adoceanResponse.getPrice()))
                .w(Integer.valueOf(adoceanResponse.getWidth()))
                .h(Integer.valueOf(adoceanResponse.getHeight()))
                .crid(adoceanResponse.getCrid())
                .build();
    }

    private static String getBidCurrency(AdoceanResponseAdUnit adoceanResponse) {
        return adoceanResponse.getCurrency() != null
                ? adoceanResponse.getCurrency()
                : DEFAULT_BID_CURRENCY;
    }

    private List<AdoceanResponseAdUnit> getAdoceanResponseAdUnitList(String responseBody) {
        try {
            return mapper.mapper().readValue(
                    responseBody,
                    mapper.mapper().getTypeFactory().constructCollectionType(List.class, AdoceanResponseAdUnit.class));
        } catch (IOException ex) {
            throw new PreBidException(ex.getMessage());
        }
    }
}
