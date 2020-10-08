package org.prebid.server.bidder.connectAd;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.connectad.ExtImpConnectAd;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ConnectAdBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpConnectAd>> CONNECTAD_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpConnectAd>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ConnectAdBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final String page = request.getSite() != null ? request.getSite().getPage() : null;
        final int secure = StringUtils.isNotBlank(page) && page.contains("https") ? 1 : 0;

        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> processedImps = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpConnectAd impExt = parseImpExt(imp);
                final Imp updatedImp = updateImp(imp, secure, impExt);
                processedImps.add(updatedImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        if (CollectionUtils.isNotEmpty(errors)) {
            errors.add(BidderError.badInput("Error in preprocess of Imp"));
            return Result.of(Collections.emptyList(), errors);
        }
        final BidRequest outgoingRequest = request.toBuilder().imp(processedImps).build();

        final String body = mapper.encode(outgoingRequest);

        final MultiMap headers = HttpUtil.headers();
        updateHeadersDueToRequest(headers, request);

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .headers(headers)
                        .payload(outgoingRequest)
                        .body(body)
                        .build()),
                errors);
    }

    private ExtImpConnectAd parseImpExt(Imp imp) {
        final ExtImpConnectAd extImpConnectAd;
        try {
            extImpConnectAd = mapper.mapper().convertValue(imp.getExt(), CONNECTAD_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("Impression id=%s, has invalid Ext", imp.getId()));
        }

        if (extImpConnectAd.getSiteId().equals(NumberUtils.INTEGER_ZERO)) {
            throw new PreBidException(String.format("Impression id=%s, has no siteId present", imp.getId()));
        }
        return extImpConnectAd;
    }

    private Imp updateImp(Imp imp, Integer secure, ExtImpConnectAd extImp) {

        final Imp.ImpBuilder updatedImp = imp.toBuilder().tagid(extImp.getSiteId().toString()).secure(secure);
        if (extImp.getBidfloor().compareTo(BigDecimal.ZERO) != NumberUtils.INTEGER_ZERO) {
            updatedImp.bidfloor(extImp.getBidfloor()).bidfloorcur("USD");
        }

        final Banner banner = imp.getBanner();
        if (banner == null) {
            throw new PreBidException("We need a Banner Object in the request");
        }

        if (banner.getW() == null && banner.getH() == null) {
            if (CollectionUtils.isEmpty(banner.getFormat())) {
                throw new PreBidException("At least one size is required");
            }
        }

        final Format format = banner.getFormat().get(0);
        final List<Format> slicedFormatList = banner.getFormat();

        slicedFormatList.remove(0);
        updatedImp.banner(banner.toBuilder().format(slicedFormatList).w(format.getW()).h(format.getH()).build());

        return updatedImp.build();
    }

    private void updateHeadersDueToRequest(MultiMap headers, BidRequest request) {
        final Device device = request.getDevice();

        if (device != null) {
            headers.add("User-Agent", device.getUa());
            headers.add("Accept-Language", device.getLanguage());
            if (StringUtils.isNotEmpty(device.getIp())) {
                headers.add("X-Forwarded-For", device.getIp());
            }
            if (StringUtils.isNotEmpty(device.getIpv6())) {
                headers.add("X-Forwarded-For", device.getIpv6());
            }
            if (device.getDnt() != null) {
                headers.add("DNT", device.getDnt().toString());
            } else {
                headers.add("DNT", "0");
            }
        }
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }

        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || bidResponse.getSeatbid() == null) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, BidType.banner, bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}

