package org.prebid.server.bidder.dxkulture;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.uritemplate.UriTemplate;
import io.vertx.uritemplate.Variables;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.dxkulture.ExtImpDxKulture;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;
import org.prebid.server.util.UriTemplateUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DxKultureBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpDxKulture>> DXKULTURE_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String X_OPENRTB_VERSION = "2.5";

    private final UriTemplate endpointUrlTemplate;
    private final JacksonMapper mapper;

    public DxKultureBidder(String endpointUrl, JacksonMapper mapper) {
        HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.endpointUrlTemplate = UriTemplateUtil.createTemplate(endpointUrl, "queryParams");
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> result = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            final String uri;
            try {
                uri = getUri(parseImpExt(imp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }

            result.add(BidderUtil.defaultRequest(request, resolveHeaders(request), uri, mapper));
        }

        return Result.of(result, errors);
    }

    private ExtImpDxKulture parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), DXKULTURE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private String getUri(ExtImpDxKulture extImpDxKulture) {
        final Map<String, String> queryParams = Map.of(
                "publisher_id", extImpDxKulture.getPublisherId(),
                "placement_id", extImpDxKulture.getPlacementId());

        return endpointUrlTemplate.expandToString(Variables.variables().set("queryParams", queryParams));
    }

    private static MultiMap resolveHeaders(BidRequest bidRequest) {
        final Device device = bidRequest.getDevice();
        final Site site = bidRequest.getSite();
        final MultiMap headers = HttpUtil.headers();

        headers.set(HttpUtil.X_OPENRTB_VERSION_HEADER, X_OPENRTB_VERSION);

        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER,
                ObjectUtil.getIfNotNull(device, Device::getUa));
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER,
                ObjectUtil.getIfNotNull(device, Device::getIpv6));
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER,
                ObjectUtil.getIfNotNull(device, Device::getIp));

        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.REFERER_HEADER,
                ObjectUtil.getIfNotNull(site, Site::getRef));
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ORIGIN_HEADER,
                ObjectUtil.getIfNotNull(site, Site::getDomain));

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;
        try {
            bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bids = extractBids(bidResponse, errors);

        return Result.of(bids, errors);
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid makeBidderBid(Bid bid, String currency, List<BidderError> errors) {
        try {
            return BidderBid.of(bid, resolveBidType(bid), currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static BidType resolveBidType(Bid bid) throws PreBidException {
        final Integer markupType = bid.getMtype();
        if (markupType == null) {
            throw new PreBidException("Missing MType for bid: " + bid.getId());
        }
        return switch (markupType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            default ->
                    throw new PreBidException("Unsupported MType: %s, for bid: %s".formatted(markupType, bid.getId()));
        };
    }
}
