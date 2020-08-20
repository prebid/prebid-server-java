package org.prebid.server.bidder.adocean;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.netty.handler.codec.http.HttpResponseStatus;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class AdoceanBidder implements Bidder<Void> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdocean>> ADOCEAN_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAdocean>>() {
            };
    private static final String VERSION = "1.0.0";
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
        if (CollectionUtils.isEmpty(request.getImp())) {
            return Result.emptyWithError(BidderError.badInput("No impression in the bid request"));
        }

        final User user = request.getUser();
        final ExtUser extUser = user != null ? user.getExt() : null;
        final String consent = extUser != null ? extUser.getConsent() : null;
        final String consentString = StringUtils.isNotBlank(consent) ? consent : "";

        final List<HttpRequest<Void>> httpRequests = new ArrayList<>();
        for (Imp imp : request.getImp()) {
            try {
                final ExtImpAdocean extImpAdocean = parseImpExt(imp);
                if (addRequestAndCheckIfDuplicates(httpRequests, extImpAdocean, imp.getId())) {
                    continue;
                }
                httpRequests.add(createSingleRequest(request, imp, extImpAdocean, consentString));
            } catch (PreBidException e) {
                return Result.emptyWithError(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(httpRequests, Collections.emptyList());
    }

    private ExtImpAdocean parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ADOCEAN_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private boolean addRequestAndCheckIfDuplicates(List<HttpRequest<Void>> httpRequests, ExtImpAdocean extImpAdocean,
                                                   String impid) {
        for (HttpRequest<Void> request : httpRequests) {
            List<NameValuePair> params = null;
            try {
                URIBuilder uriBuilder = new URIBuilder(request.getUri());
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

                    final String url = HttpUtil.encodeUrl(String.valueOf(params));
                    if (url.length() < MAX_URI_LENGTH) {
                        return true;
                    }
                    queryParams.add(new BasicNameValuePair("aid", extImpAdocean.getSlaveId() + ":" + impid));
                }

            } catch (URISyntaxException e) {
                throw new PreBidException(e.getMessage());
            }
        }
        return false;
    }

    private HttpRequest<Void> createSingleRequest(BidRequest request, Imp imp, ExtImpAdocean extImpAdocean,
                                                  String consentString) {

        return HttpRequest.<Void>builder()
                .method(HttpMethod.GET)
                .uri(buildUrl(imp.getId(), extImpAdocean, consentString, request.getTest(), request.getUser()))
                .headers(getHeaders(request))
                .build();
    }

    private String buildUrl(String impid, ExtImpAdocean extImpAdocean, String consentString, Integer test, User user) {
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

        return uriBuilder.toString();
    }

    private static MultiMap getHeaders(BidRequest request) {
        final MultiMap headers = HttpUtil.headers();
        if (request.getDevice() != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER.toString(),
                    request.getDevice().getUa());

            if (StringUtils.isNotBlank(request.getDevice().getIp())) {
                HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER,
                        request.getDevice().getIp());
            } else if (StringUtils.isNotBlank(request.getDevice().getIpv6())) {
                HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER,
                        request.getDevice().getIpv6());
            }
        }

        if (request.getSite() != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.REFERER_HEADER, request.getSite().getPage());
        }
        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<Void> httpCall, BidRequest bidRequest) {
        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        } else if (statusCode == HttpResponseStatus.BAD_REQUEST.code()) {
            return Result.emptyWithError(BidderError.badInput("Invalid request."));
        } else if (statusCode != HttpResponseStatus.OK.code()) {
            return Result.emptyWithError(BidderError.badServerResponse(String.format("Unexpected HTTP status %s.",
                    statusCode)));
        }

        List<NameValuePair> params;
        try {
            params = URLEncodedUtils.parse(new URI(httpCall.getRequest().getUri()), StandardCharsets.UTF_8);
        } catch (URISyntaxException e) {
            return Result.emptyWithError(BidderError.badInput(e.getMessage()));
        }

        final Map<String, String> auctionIds = params != null ? params.stream()
                .filter(param -> param.getName().equals("aid"))
                .map(param -> param.getValue().split(":"))
                .collect(Collectors.toMap(name -> name[0], value -> value[1])) : null;

        List<AdoceanResponseAdUnit> adoceanResponses;
        try {
            adoceanResponses = getAdoceanResponseAdUnitList(httpCall.getResponse().getBody());
        } catch (PreBidException e) {
            return Result.emptyWithError(BidderError
                    .badServerResponse("Failed to decode: No content to map due to end-of-input"));
        }

        final List<BidderBid> bidderBids = adoceanResponses.stream()
                .filter(adoceanResponse -> !adoceanResponse.getError().equals("true"))
                .filter(adoceanResponse -> auctionIds != null
                        && StringUtils.isNotBlank(auctionIds.get(adoceanResponse.getId())))
                .map(adoceanResponse -> BidderBid.of(createBid(auctionIds, adoceanResponse), BidType.banner,
                        getBidCurrency(adoceanResponse)))
                .collect(Collectors.toList());

        return Result.of(bidderBids, Collections.emptyList());
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

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
