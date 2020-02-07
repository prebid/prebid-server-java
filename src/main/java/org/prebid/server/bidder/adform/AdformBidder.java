package org.prebid.server.bidder.adform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.adform.model.AdformBid;
import org.prebid.server.bidder.adform.model.UrlParameters;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.adform.ExtImpAdform;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Adform {@link Bidder} implementation.
 */
public class AdformBidder implements Bidder<Void> {

    private static final String VERSION = "0.1.3";
    private static final String BANNER = "banner";
    private static final String DEFAULT_CURRENCY = "USD";

    private static final TypeReference<ExtPrebid<?, ExtImpAdform>> ADFORM_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAdform>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    private final AdformRequestUtil requestUtil;
    private final AdformHttpUtil httpUtil;

    public AdformBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);

        this.requestUtil = new AdformRequestUtil(mapper);
        this.httpUtil = new AdformHttpUtil(mapper);
    }

    /**
     * Makes the HTTP requests which should be made to fetch bids.
     * <p>
     * Creates GET http request with all parameters in url and headers with empty body.
     */
    @Override
    public Result<List<HttpRequest<Void>>> makeHttpRequests(BidRequest request) {
        final List<Imp> imps = request.getImp();
        final Result<List<ExtImpAdform>> extImpAdformsResult = getExtImpAdforms(imps);
        final List<ExtImpAdform> extImpAdforms = extImpAdformsResult.getValue();
        final List<BidderError> errors = extImpAdformsResult.getErrors();

        if (extImpAdforms.isEmpty()) {
            return Result.of(Collections.emptyList(), errors);
        }

        final String currency = resolveRequestCurrency(request.getCur());
        final Device device = request.getDevice();
        final ExtUser extUser = requestUtil.getExtUser(request.getUser());
        final String url = httpUtil.buildAdformUrl(
                UrlParameters.builder()
                        .masterTagIds(getMasterTagIds(extImpAdforms))
                        .keyValues(getKeyValues(extImpAdforms))
                        .keyWords(getKeyWords(extImpAdforms))
                        .priceTypes(getPriceType(extImpAdforms))
                        .endpointUrl(endpointUrl)
                        .tid(getTid(request.getSource()))
                        .ip(getIp(device))
                        .advertisingId(getIfa(device))
                        .secure(getSecure(imps))
                        .gdprApplies(requestUtil.getGdprApplies(request.getRegs()))
                        .consent(requestUtil.getConsent(extUser))
                        .currency(currency)
                        .build());

        final MultiMap headers = httpUtil.buildAdformHeaders(
                VERSION,
                getUserAgent(device),
                getIp(device),
                getReferer(request.getSite()),
                getUserId(request.getUser()),
                requestUtil.getAdformDigitrust(extUser));

        return Result.of(Collections.singletonList(
                HttpRequest.<Void>builder()
                        .method(HttpMethod.GET)
                        .uri(url)
                        .body(null)
                        .headers(headers)
                        .payload(null)
                        .build()),
                errors);
    }

    private List<String> getKeyValues(List<ExtImpAdform> extImpAdforms) {
        return extImpAdforms.stream().map(ExtImpAdform::getKeyValues).collect(Collectors.toList());
    }

    private List<String> getKeyWords(List<ExtImpAdform> extImpAdforms) {
        return extImpAdforms.stream().map(ExtImpAdform::getKeyWords).collect(Collectors.toList());
    }

    /**
     * Converts Adform Response format to {@link List} of {@link BidderBid}s with {@link List} of errors.
     * Returns empty result {@link List} in case of "No Content" response status.
     * Returns empty result {@link List} with errors in case of response status different from "OK" or "No Content".
     */
    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<Void> httpCall, BidRequest bidRequest) {
        final HttpResponse httpResponse = httpCall.getResponse();

        final List<AdformBid> adformBids;
        try {
            adformBids = mapper.mapper().readValue(
                    httpResponse.getBody(),
                    mapper.mapper().getTypeFactory().constructCollectionType(List.class, AdformBid.class));
        } catch (JsonProcessingException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
        return Result.of(toBidderBid(adformBids, bidRequest.getImp()), Collections.emptyList());
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }

    /**
     * Retrieves from {@link Imp} and filter not valid {@link ExtImpAdform} and returns list result with errors.
     */
    private Result<List<ExtImpAdform>> getExtImpAdforms(List<Imp> imps) {
        final List<BidderError> errors = new ArrayList<>();
        final List<ExtImpAdform> extImpAdforms = new ArrayList<>();
        for (final Imp imp : imps) {
            if (imp.getBanner() == null) {
                errors.add(BidderError.badInput(String.format(
                        "Adform adapter supports only banner Imps for now. Ignoring Imp ID=%s", imp.getId())));
                continue;
            }
            final ExtImpAdform extImpAdform;
            try {
                extImpAdform = mapper.mapper().convertValue(imp.getExt(), ADFORM_EXT_TYPE_REFERENCE).getBidder();
            } catch (IllegalArgumentException e) {
                errors.add(BidderError.badInput(String.format("Error occurred parsing adform parameters %s",
                        e.getMessage())));
                continue;
            }

            final Long mid = extImpAdform.getMasterTagId();
            if (mid == null || mid <= 0) {
                errors.add(BidderError.badInput(String.format("master tag(placement) id is invalid=%s", mid)));
                continue;
            }
            extImpAdforms.add(extImpAdform);
        }

        return Result.of(extImpAdforms, errors);
    }

    /**
     * Resolves a currency that should be forwarded to bidder. Default - USD, if request
     * doesn't contain USD - select the top level currency (first one);
     */
    private static String resolveRequestCurrency(List<String> currencies) {
        return CollectionUtils.isNotEmpty(currencies) && !currencies.contains(DEFAULT_CURRENCY)
                ? currencies.get(0)
                : DEFAULT_CURRENCY;
    }

    /**
     * Converts {@link ExtImpAdform} {@link List} to master tag {@link List}.
     */
    private List<Long> getMasterTagIds(List<ExtImpAdform> extImpAdforms) {
        return extImpAdforms.stream().map(ExtImpAdform::getMasterTagId).collect(Collectors.toList());
    }

    /**
     * Converts {@link ExtImpAdform} {@link List} to price types {@link List}.
     */
    private List<String> getPriceType(List<ExtImpAdform> extImpAdforms) {
        return extImpAdforms.stream().map(ExtImpAdform::getPriceType).collect(Collectors.toList());
    }

    /**
     * Retrieves referer from {@link Site}.
     */
    private String getReferer(Site site) {
        return site != null ? site.getPage() : "";
    }

    /**
     * Retrieves userId from {@link User}.
     */
    private String getUserId(User user) {
        return user != null ? user.getBuyeruid() : "";
    }

    /**
     * Defines if request should be secured from {@link List} of {@link Imp}s.
     */
    private Boolean getSecure(List<Imp> imps) {
        return imps.stream()
                .anyMatch(imp -> Objects.equals(imp.getSecure(), 1));
    }

    /**
     * Retrieves userAgent from {@link Device}.
     */
    private String getUserAgent(Device device) {
        return device != null ? ObjectUtils.firstNonNull(device.getUa(), "") : "";
    }

    /**
     * Retrieves ip from {@link Device}.
     */
    private String getIp(Device device) {
        return device != null ? ObjectUtils.firstNonNull(device.getIp(), "") : "";
    }

    /**
     * Retrieves ifs from {@link Device}.
     */
    private String getIfa(Device device) {
        return device != null ? ObjectUtils.firstNonNull(device.getIfa(), "") : "";
    }

    /**
     * Retrieves tid from {@link Source}.
     */
    private String getTid(Source source) {
        return source != null ? ObjectUtils.firstNonNull(source.getTid(), "") : "";
    }

    /**
     * Converts {@link AdformBid} to {@link List} of {@link BidderBid}.
     */
    private List<BidderBid> toBidderBid(List<AdformBid> adformBids, List<Imp> imps) {
        final List<BidderBid> bidderBids = new ArrayList<>();

        final String currency = CollectionUtils.isNotEmpty(adformBids) ? adformBids.get(0).getWinCur() : null;

        for (int i = 0; i < adformBids.size(); i++) {
            final AdformBid adformBid = adformBids.get(i);
            if (StringUtils.isEmpty(adformBid.getBanner()) || !Objects.equals(adformBid.getResponse(), BANNER)) {
                continue;
            }
            final Imp imp = imps.get(i);
            bidderBids.add(BidderBid.of(Bid.builder()
                            .id(imp.getId())
                            .impid(imp.getId())
                            .price(adformBid.getWinBid())
                            .adm(adformBid.getBanner())
                            .w(adformBid.getWidth())
                            .h(adformBid.getHeight())
                            .dealid(adformBid.getDealId())
                            .crid(adformBid.getWinCrid())
                            .build(),
                    BidType.banner,
                    currency));
        }

        return bidderBids;
    }
}
