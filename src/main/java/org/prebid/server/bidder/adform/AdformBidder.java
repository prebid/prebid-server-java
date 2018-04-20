package org.prebid.server.bidder.adform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderUtil;
import org.prebid.server.bidder.adform.model.AdformBid;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adform.ExtImpAdform;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.io.IOException;
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

    private static final String VERSION = "0.1.1";
    private static final String BANNER = "banner";
    private static final TypeReference<ExtPrebid<?, ExtImpAdform>> ADFORM_EXT_TYPE_REFERENCE = new
            TypeReference<ExtPrebid<?, ExtImpAdform>>() {
            };

    private final String endpointUrl;

    public AdformBidder(String endpointUrl) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    /**
     * Makes the HTTP requests which should be made to fetch bids.
     * <p>
     * Creates GET http request with all parameters in url and headers with empty body.
     */
    @Override
    public Result<List<HttpRequest<Void>>> makeHttpRequests(BidRequest request) {

        final List<Imp> imps = request.getImp();
        final ResultWithErrors<List<ExtImpAdform>> extImpAdformsResult = getExtImpAdforms(imps);
        final List<ExtImpAdform> extImpAdforms = extImpAdformsResult.result;
        final List<String> errors = extImpAdformsResult.errors;

        if (extImpAdforms.size() == 0) {
            return Result.of(Collections.emptyList(), BidderUtil.errors(errors));
        }
        final Device device = request.getDevice();
        final String url = AdformHttpUtil.buildAdformUrl(
                getMasterTagIds(extImpAdforms),
                endpointUrl,
                getTid(request.getSource()),
                getIp(device),
                getIfa(device),
                getSecure(imps));

        final MultiMap headers = AdformHttpUtil.buildAdformHeaders(
                VERSION,
                getUserAgent(device),
                getIp(device),
                getReferer(request.getSite()),
                getUserId(request.getUser()));

        return Result.of(
                Collections.singletonList(HttpRequest.of(HttpMethod.GET, url, null, headers, null)),
                BidderUtil.errors(errors));
    }

    /**
     * Converts Adform Response format to {@link List} of {@link BidderBid}s with {@link List} of errors.
     * Returns empty result {@link List} in case of "No Content" response status.
     * Returns empty result {@link List} with errors in case of response status different from "OK" or "No Content"
     */
    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<Void> httpCall, BidRequest bidRequest) {
        final HttpResponse httpResponse = httpCall.getResponse();
        final int responseStatusCode = httpResponse.getStatusCode();
        if (responseStatusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }
        if (responseStatusCode != HttpResponseStatus.OK.code()) {
            return Result.of(Collections.emptyList(), Collections.singletonList(BidderError.create(
                    String.format("unexpected status code: %d. Run with request.debug = 1 for more info",
                            responseStatusCode))));
        }
        final List<AdformBid> adformBids;
        try {
            adformBids = Json.mapper.readValue(httpResponse.getBody(),
                    Json.mapper.getTypeFactory().constructCollectionType(List.class, AdformBid.class));
        } catch (IOException e) {
            return Result.of(Collections.emptyList(), Collections.singletonList(BidderError.create(e.getMessage())));
        }
        return Result.of(toBidderBid(adformBids, bidRequest.getImp()), Collections.emptyList());
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }

    /**
     * Retrieves from {@link Imp} and filter not valid {@link ExtImpAdform} and returns list result with errors
     */
    private ResultWithErrors<List<ExtImpAdform>> getExtImpAdforms(List<Imp> imps) {
        final List<String> errors = new ArrayList<>();
        final List<ExtImpAdform> extImpAdforms = new ArrayList<>();
        for (final Imp imp : imps) {
            if (imp.getBanner() == null) {
                errors.add(String.format(
                        "Adform adapter supports only banner Imps for now. Ignoring Imp ID=%s", imp.getId()));
                continue;
            }
            final ExtImpAdform extImpAdform;
            try {
                extImpAdform = Json.mapper.<ExtPrebid<?, ExtImpAdform>>convertValue(imp.getExt(),
                        ADFORM_EXT_TYPE_REFERENCE).getBidder();
            } catch (IllegalArgumentException e) {
                errors.add(String.format("Error occurred parsing adform parameters %s", e.getMessage()));
                continue;
            }

            final Long mid = extImpAdform.getMasterTagId();
            if (mid == null || mid <= 0) {
                errors.add(String.format("master tag(placement) id is invalid=%s", mid));
                continue;
            }
            extImpAdforms.add(extImpAdform);
        }

        return ResultWithErrors.of(extImpAdforms, errors);
    }

    /**
     * Converts {@link ExtImpAdform} {@link List} to master tag {@link List}
     */
    private List<String> getMasterTagIds(List<ExtImpAdform> extImpAdforms) {
        return extImpAdforms.stream()
                .map(extImpAdform -> extImpAdform.getMasterTagId().toString())
                .collect(Collectors.toList());
    }

    /**
     * Retrieves referer from {@link Site}
     */
    private String getReferer(Site site) {
        return site != null ? site.getPage() : "";
    }

    /**
     * Retrieves userId from {@link User}
     */
    private String getUserId(User user) {
        return user != null ? user.getBuyeruid() : "";
    }

    /**
     * Defines if request should be secured from {@link List} of {@link Imp}s
     */
    private Boolean getSecure(List<Imp> imps) {
        return imps.stream()
                .anyMatch(imp -> Objects.equals(imp.getSecure(), 1));
    }

    /**
     * Retrieves userAgent from {@link Device}
     */
    private String getUserAgent(Device device) {
        return device != null ? ObjectUtils.firstNonNull(device.getUa(), "") : "";
    }

    /**
     * Retrieves ip from {@link Device}
     */
    private String getIp(Device device) {
        return device != null ? ObjectUtils.firstNonNull(device.getIp(), "") : "";
    }

    /**
     * Retrieves ifs from {@link Device}
     */
    private String getIfa(Device device) {
        return device != null ? ObjectUtils.firstNonNull(device.getIfa(), "") : "";
    }

    /**
     * Retrieves tid from {@link Source}
     */
    private String getTid(Source source) {
        return source != null ? ObjectUtils.firstNonNull(source.getTid(), "") : "";
    }

    /**
     * Converts {@link AdformBid} to {@link List} of {@link BidderBid}.
     */
    private List<BidderBid> toBidderBid(List<AdformBid> adformBids, List<Imp> imps) {
        final List<BidderBid> bidderBids = new ArrayList<>();
        for (int i = 0; i < adformBids.size(); i++) {
            final AdformBid adformBid = adformBids.get(i);
            if (StringUtils.isEmpty(adformBid.getBanner())
                    || !Objects.equals(adformBid.getResponse(), BANNER)) {
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
                            .build(),
                    BidType.banner));
        }
        return bidderBids;
    }

    /**
     * Class which holds result with {@link List} of errors
     */
    @AllArgsConstructor(staticName = "of")
    @Value
    private static class ResultWithErrors<T> {
        T result;
        List<String> errors;
    }
}
