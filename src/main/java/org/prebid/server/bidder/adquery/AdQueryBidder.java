package org.prebid.server.bidder.adquery;

import com.fasterxml.jackson.core.io.BigDecimalParser;
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
        return AdQueryRequest.builder()
                .v(PREBID_VERSION)
                .placementCode(extImpAdQuery.getPlacementId())
                .auctionId(StringUtils.EMPTY)
                .type(extImpAdQuery.getType())
                .adUnitCode(imp.getTagid())
                .bidQid(ObjectUtil.getIfNotNullOrDefault(bidRequest.getUser(), User::getId, () -> StringUtils.EMPTY))
                .bidId(String.format("%s%s", bidRequest.getId(), imp.getId()))
                .bidder(BIDDER_NAME)
                .bidderRequestId(bidRequest.getId())
                .bidRequestsCount(1)
                .bidderRequestsCount(1)
                .sizes(getImpSizes(imp))
                .bidIp(ObjectUtil.getIfNotNull(bidRequest.getDevice(), Device::getIp))
                .bidIpv6(ObjectUtil.getIfNotNull(bidRequest.getDevice(), Device::getIpv6))
                .bidUa(ObjectUtil.getIfNotNull(bidRequest.getDevice(), Device::getUa))
                .bidPageUrl(ObjectUtil.getIfNotNull(bidRequest.getSite(), Site::getPage))
                .build();

    }

    private String getImpSizes(Imp imp) {
        final Banner banner = imp.getBanner();
        if (banner == null) {
            return StringUtils.EMPTY;
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

    private MultiMap resolveHeader(Device device) {
        final MultiMap headers = HttpUtil.headers();
        headers.add(HttpUtil.X_OPENRTB_VERSION_HEADER, ORTB_VERSION);

        if (Objects.nonNull(device) && StringUtils.isNotBlank(device.getIp()) && device.getIp().length() > 0) {
            headers.add(HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
        }

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
        if (adQueryResponse == null || Objects.isNull(adQueryResponse.getData())) {
            return Collections.emptyList();
        }

        final AdQueryDataResponse data = adQueryResponse.getData();
        final Bid bid = Bid.builder()
                .id(data.getRequestId())
                .impid(resoleImpId(bidRequest, data))
                .price(BigDecimalParser.parse(data.getCpm()))
                .adm(String.format(ADM_TEMPLATE, data.getAdqLib(), data.getTag()))
                .adomain(data.getAdDomains())
                .crid(String.format("%d", data.getCreationId()))
                .w(parseMeasure(ObjectUtil.getIfNotNull(data.getAdQueryMediaType(), AdQueryMediaType::getWidth)))
                .h(parseMeasure(ObjectUtil.getIfNotNull(data.getAdQueryMediaType(), AdQueryMediaType::getHeight)))
                .build();

        return Collections.singletonList(BidderBid.of(bid, resolveMediaType(data),
                StringUtils.isNotBlank(data.getCurrency()) ? data.getCurrency() : DEFAULT_CURRENCY));
    }

    private static String resoleImpId(BidRequest bidRequest, AdQueryDataResponse data) {
        return Objects.nonNull(data.getRequestId())
                ? bidRequest.getId().replaceAll(data.getRequestId(), StringUtils.EMPTY)
                : bidRequest.getId();
    }

    private static Integer parseMeasure(String measure) {
        try {
            return Integer.valueOf(measure);
        } catch (NumberFormatException e) {
            throw new PreBidException("Value of measure: %s can not be parsed.".formatted(measure));
        }
    }

    private static BidType resolveMediaType(AdQueryDataResponse data) {
        if (data.getAdQueryMediaType().getName() != BidType.banner) {
            throw new PreBidException(String.format("Unsupported MediaType: %s", data.getAdQueryMediaType().getName()));
        }
        return data.getAdQueryMediaType().getName();
    }
}
