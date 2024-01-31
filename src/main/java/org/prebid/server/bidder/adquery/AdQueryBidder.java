package org.prebid.server.bidder.adquery;

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
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.adquery.model.request.AdQueryRequest;
import org.prebid.server.bidder.adquery.model.response.AdQueryDataResponse;
import org.prebid.server.bidder.adquery.model.response.AdQueryMediaType;
import org.prebid.server.bidder.adquery.model.response.AdQueryResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adquery.ExtImpAdQuery;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class AdQueryBidder implements Bidder<AdQueryRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdQuery>> AD_QUERY_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String PREBID_VERSION = "server";
    private static final String BIDDER_NAME = "adquery";
    private static final String DEFAULT_CURRENCY = "PLN";
    private static final String ORTB_VERSION = "2.5";
    private static final String ADM_TEMPLATE = "<script src=\"%s\"></script>%s";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AdQueryBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<AdQueryRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<AdQueryRequest>> httpRequests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            final ExtImpAdQuery extImpAdQuery;
            try {
                extImpAdQuery = parseImpExt(imp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }
            httpRequests.add(createRequest(request, imp, extImpAdQuery));
        }

        return Result.of(httpRequests, errors);
    }

    private ExtImpAdQuery parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), AD_QUERY_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private HttpRequest<AdQueryRequest> createRequest(BidRequest bidRequest, Imp imp, ExtImpAdQuery extImpAdQuery) {
        final AdQueryRequest outgoingRequest = createAdQueryRequest(bidRequest, imp, extImpAdQuery);

        return HttpRequest.<AdQueryRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(resolveHeader(bidRequest.getDevice()))
                .impIds(BidderUtil.impIds(bidRequest))
                .payload(outgoingRequest)
                .body(mapper.encodeToBytes(outgoingRequest))
                .build();
    }

    private AdQueryRequest createAdQueryRequest(BidRequest bidRequest, Imp imp, ExtImpAdQuery extImpAdQuery) {
        final Optional<Device> optionalDevice = Optional.ofNullable(bidRequest.getDevice());
        return AdQueryRequest.builder()
                .v(PREBID_VERSION)
                .placementCode(extImpAdQuery.getPlacementId())
                .auctionId(StringUtils.EMPTY)
                .type(extImpAdQuery.getType())
                .adUnitCode(imp.getTagid())
                .bidQid(Optional.ofNullable(bidRequest.getUser()).map(User::getId).orElse(StringUtils.EMPTY))
                .bidId(bidRequest.getId() + imp.getId())
                .bidder(BIDDER_NAME)
                .bidderRequestId(bidRequest.getId())
                .bidRequestsCount(1)
                .bidderRequestsCount(1)
                .sizes(getImpSizes(imp))
                .bidIp(optionalDevice.map(Device::getIp).orElse(null))
                .bidIpv6(optionalDevice.map(Device::getIpv6).orElse(null))
                .bidUa(optionalDevice.map(Device::getUa).orElse(null))
                .bidPageUrl(Optional.ofNullable(bidRequest.getSite()).map(Site::getPage).orElse(null))
                .build();
    }

    private String getImpSizes(Imp imp) {
        final Banner banner = imp.getBanner();
        if (banner == null) {
            return StringUtils.EMPTY;
        }

        final List<Format> format = banner.getFormat();
        if (CollectionUtils.isNotEmpty(format)) {
            return format.stream()
                    .map(singleFormat -> "%sx%s".formatted(
                            ObjectUtils.defaultIfNull(singleFormat.getW(), 0),
                            ObjectUtils.defaultIfNull(singleFormat.getH(), 0)))
                    .collect(Collectors.joining("_"));
        }

        final Integer w = banner.getW();
        final Integer h = banner.getH();
        if (w != null && h != null) {
            return "%sx%s".formatted(w, h);
        }

        return StringUtils.EMPTY;
    }

    private MultiMap resolveHeader(Device device) {
        final MultiMap headers = HttpUtil.headers();
        headers.add(HttpUtil.X_OPENRTB_VERSION_HEADER, ORTB_VERSION);

        Optional.ofNullable(device)
                .map(Device::getIp)
                .map(StringUtils::isNotBlank)
                .ifPresent(ip -> headers.add(HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp()));

        return headers;
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<AdQueryRequest> httpCall, BidRequest bidRequest) {
        try {
            final AdQueryResponse bidResponse = mapper.decodeValue(
                    httpCall.getResponse().getBody(), AdQueryResponse.class);
            return Result.withValues(extractBids(bidResponse, bidRequest));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(AdQueryResponse adQueryResponse, BidRequest bidRequest) {
        if (adQueryResponse == null || adQueryResponse.getData() == null) {
            return Collections.emptyList();
        }

        final AdQueryDataResponse data = adQueryResponse.getData();
        final Bid bid = Bid.builder()
                .id(data.getRequestId())
                .impid(resolveImpId(bidRequest, data))
                .price(data.getCpm())
                .adm(ADM_TEMPLATE.formatted(data.getAdqLib(), data.getTag()))
                .adomain(data.getAdDomains())
                .crid(data.getCreationId())
                .w(ObjectUtil.getIfNotNull(data.getAdQueryMediaType(), AdQueryMediaType::getWidth))
                .h(ObjectUtil.getIfNotNull(data.getAdQueryMediaType(), AdQueryMediaType::getHeight))
                .build();

        return Collections.singletonList(BidderBid.of(bid, resolveMediaType(data.getAdQueryMediaType()),
                StringUtils.isNotBlank(data.getCurrency()) ? data.getCurrency() : DEFAULT_CURRENCY));
    }

    private static String resolveImpId(BidRequest bidRequest, AdQueryDataResponse data) {
        return data.getRequestId() != null
                ? bidRequest.getId().replaceAll(data.getRequestId(), StringUtils.EMPTY)
                : bidRequest.getId();
    }

    private static BidType resolveMediaType(AdQueryMediaType mediaType) {
        if (mediaType.getName() != BidType.banner) {
            throw new PreBidException(String.format("Unsupported MediaType: %s", mediaType.getName()));
        }
        return BidType.banner;
    }
}
