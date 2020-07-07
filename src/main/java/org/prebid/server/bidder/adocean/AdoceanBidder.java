package org.prebid.server.bidder.adocean;

import com.fasterxml.jackson.core.JsonProcessingException;
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

        String consentString = "";
        final User user = request.getUser();
        if (user != null && StringUtils.isNotBlank(extUser(user.getExt()).getConsent())) {
            consentString = extUser(user.getExt()).getConsent();
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<Void>> httpRequests = new ArrayList<>();
        for (Imp imp : request.getImp()) {
            try {
                httpRequests.add(createSingleRequest(httpRequests, request, imp, consentString));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                return Result.of(Collections.emptyList(), errors);
            }
        }

        return Result.of(httpRequests, errors);
    }

    private ExtUser extUser(ObjectNode extNode) {
        try {
            return extNode != null ? mapper.mapper().treeToValue(extNode, ExtUser.class) : null;
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private HttpRequest<Void> createSingleRequest(List<HttpRequest<Void>> httpRequests, BidRequest request, Imp imp,
                                                  String consentString) {
        final ExtImpAdocean extImpAdocean = parseImpExt(imp);

        if (isRequestAdded(httpRequests, extImpAdocean, imp.getId())) {
            throw new PreBidException("Request already exists");
        }

        return HttpRequest.<Void>builder()
                .method(HttpMethod.GET)
                .uri(buildUrl(imp.getId(), extImpAdocean, consentString, request.getTest(), request.getUser()))
                .body(null)
                .headers(getHeaders(request))
                .payload(null)
                .build();
    }

    private ExtImpAdocean parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ADOCEAN_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private boolean isRequestAdded(List<HttpRequest<Void>> httpRequests, ExtImpAdocean extImpAdocean, String impid) {
        for (final HttpRequest request : httpRequests) {
            List<NameValuePair> params = null;
            try {
                params = URLEncodedUtils.parse(new URI(request.getUri()), StandardCharsets.UTF_8);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            final String masterId = params != null ? params.stream()
                    .filter(param -> param.getName().equals("id"))
                    .findFirst()
                    .map(NameValuePair::getValue)
                    .orElse(null) : null;

            if (masterId != null && masterId.equals(extImpAdocean.getMasterId())) {
                final String newSlaveId = params.stream()
                        .filter(param -> param.getName().equals("aid"))
                        .map(param -> param.getValue().split(":")[0])
                        .filter(slaveId -> slaveId.equals(extImpAdocean.getSlaveId()))
                        .findFirst()
                        .orElse(null);
                if (StringUtils.isNotBlank(newSlaveId)) {
                    continue;
                }

                params.add(new BasicNameValuePair("aid", extImpAdocean.getSlaveId() + ":" + impid));

                final String url = HttpUtil.encodeUrl(String.valueOf(params));
                if (url.length() < MAX_URI_LENGTH) {
                    request.builder().uri(url);
                    return true;
                }
            }
        }
        return false;
    }

    private String buildUrl(String impid, ExtImpAdocean extImpAdocean, String consentString, Integer test, User user) {
        final String url = endpointUrl.replace("{{Host}}", extImpAdocean.getEmitterDomain());
        int randomizedPart = 10000000 + (int) (Math.random() * (99999999 - 10000000));
        if (test == 1) {
            randomizedPart = 10000000;
        }

        final String updateUrl = String.format("%s%s%s%s", url, "/_", randomizedPart, "/ad.json");

        final List<String> params = new ArrayList<>();
        params.add("pbsrv_v=" + VERSION);
        params.add("id=" + extImpAdocean.getMasterId());
        params.add("nc=1");
        params.add("nosecure=1");
        params.add("aid=" + extImpAdocean.getSlaveId() + ":" + impid);

        if (StringUtils.isNotBlank(consentString)) {
            params.add("gdpr_consent=" + consentString);
            params.add("gdpr=1");
        }
        if (user != null && StringUtils.isNotBlank(user.getBuyeruid())) {
            params.add("hcuserid=" + user.getBuyeruid());
        }

        final String urlParams = params.stream().sorted().collect(Collectors.joining("&"));
        return String.format("%s?%s", updateUrl, urlParams);
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

        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bidderBids = new ArrayList<>();
        for (final AdoceanResponseAdUnit adoceanResponse : adoceanResponses) {
            if (adoceanResponse.getError().equals("true")) {
                continue;
            }

            if (auctionIds != null && StringUtils.isNotBlank(auctionIds.get(adoceanResponse.getId()))) {
                final BigDecimal price = new BigDecimal(adoceanResponse.getPrice());
                final Integer width = Integer.valueOf(adoceanResponse.getWidth());
                final Integer height = Integer.valueOf(adoceanResponse.getHeight());

                final Bid updatedBid = Bid.builder()
                        .id(adoceanResponse.getId())
                        .impid(auctionIds.get(adoceanResponse.getId()))
                        .adm(getAdm(adoceanResponse))
                        .price(price)
                        .w(width)
                        .h(height)
                        .crid(adoceanResponse.getCrid())
                        .build();

                final String bidCurrency = adoceanResponse.getCurrency() != null
                        ? adoceanResponse.getCurrency()
                        : DEFAULT_BID_CURRENCY;
                final BidderBid bidderBid = BidderBid.of(updatedBid, BidType.banner, bidCurrency);
                bidderBids.add(bidderBid);
            }
        }
        return Result.of(bidderBids, errors);
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

    private String getAdm(AdoceanResponseAdUnit adoceanResponse) {
        final StringBuilder measurementCode = new StringBuilder();
        measurementCode.append(" <script>")
                .append(" +function() {")
                .append(" var wu = \"%s\";")
                .append(" var su = \"%s\".replace(/\\[TIMESTAMP\\]/, Date.now());")
                .append(" if (wu && !(navigator.sendBeacon && navigator.sendBeacon(wu))) {")
                .append(" (new Image(1,1)).src = wu")
                .append(" }")
                .append(" if (su && !(navigator.sendBeacon && navigator.sendBeacon(su))) {")
                .append(" (new Image(1,1)).src = su")
                .append(" }")
                .append(" }();")
                .append(" </script>");

        return String.format(measurementCode.toString(), adoceanResponse.getWinUrl(), adoceanResponse.getStatsUrl())
                + HttpUtil.decodeUrl(adoceanResponse.getCode());
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
