package org.prebid.server.bidder.yahoossp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.prebid.server.proto.openrtb.ext.request.yahoossp.ExtImpYahooSSP;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class YahooSSPBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpYahooSSP>> YAHOOSSP_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpYahooSSP>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public YahooSSPBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<HttpRequest<BidRequest>> bidRequests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        final List<Imp> impList = bidRequest.getImp();
        for (int i = 0; i < impList.size(); i++) {
            try {
                final Imp imp = impList.get(i);
                final ExtImpYahooSSP extImpYahooSSP = parseAndValidateImpExt(imp.getExt(), i);
                final BidRequest modifiedRequest = modifyRequest(bidRequest, imp, extImpYahooSSP);
                bidRequests.add(makeHttpRequest(modifiedRequest));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(bidRequests, errors);
    }

    private ExtImpYahooSSP parseAndValidateImpExt(ObjectNode impExtNode, int index) {
        final ExtImpYahooSSP extImpYahooSSP;
        try {
            extImpYahooSSP = mapper.mapper().convertValue(impExtNode, YAHOOSSP_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("imp #%s: %s", index, e.getMessage()));
        }

        final String dcn = extImpYahooSSP.getDcn();
        if (StringUtils.isBlank(dcn)) {
            throw new PreBidException(String.format("imp #%s: missing param dcn", index));
        }

        final String pos = extImpYahooSSP.getPos();
        if (StringUtils.isBlank(pos)) {
            throw new PreBidException(String.format("imp #%s: missing param pos", index));
        }

        return extImpYahooSSP;
    }

    private static BidRequest modifyRequest(BidRequest request, Imp imp, ExtImpYahooSSP extImpYahooSSP) {
        final Banner banner = imp.getBanner();
        final boolean hasBanner = banner != null;

        final Integer bannerWidth = hasBanner ? banner.getW() : null;
        final Integer bannerHeight = hasBanner ? banner.getH() : null;
        final boolean hasBannerWidthAndHeight = bannerWidth != null && bannerHeight != null;

        if (hasBannerWidthAndHeight && (bannerWidth == 0 || bannerHeight == 0)) {
            throw new PreBidException(String.format(
                    "Invalid sizes provided for Banner %sx%s", bannerWidth, bannerHeight));
        }

        final Imp.ImpBuilder impBuilder = imp.toBuilder()
                .tagid(extImpYahooSSP.getPos());

        if (hasBanner && !hasBannerWidthAndHeight) {
            impBuilder.banner(modifyBanner(banner));
        }

        final BidRequest.BidRequestBuilder requestBuilder = request.toBuilder();

        final Site site = request.getSite();
        final App app = request.getApp();
        if (site != null) {
            requestBuilder.site(site.toBuilder().id(extImpYahooSSP.getDcn()).build());
        } else if (app != null) {
            requestBuilder.app(app.toBuilder().id(extImpYahooSSP.getDcn()).build());
        }

        return requestBuilder
                .imp(Collections.singletonList(impBuilder.build()))
                .build();
    }

    private static Banner modifyBanner(Banner banner) {
        final List<Format> bannerFormats = banner.getFormat();
        if (CollectionUtils.isEmpty(bannerFormats)) {
            throw new PreBidException("No sizes provided for Banner");
        }
        final Format firstFormat = bannerFormats.get(0);

        return banner.toBuilder()
                .w(firstFormat.getW())
                .h(firstFormat.getH())
                .build();
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest outgoingRequest) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .body(mapper.encode(outgoingRequest))
                .headers(makeHeaders(outgoingRequest.getDevice()))
                .payload(outgoingRequest)
                .build();
    }

    private static MultiMap makeHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");

        final String deviceUa = device != null ? device.getUa() : null;
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, deviceUa);

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, httpCall.getRequest().getPayload()), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        final List<SeatBid> seatBids = bidResponse != null ? bidResponse.getSeatbid() : null;
        if (seatBids == null) {
            return Collections.emptyList();
        }

        if (seatBids.isEmpty()) {
            throw new PreBidException(String.format("Invalid SeatBids count: %d", seatBids.size()));
        }
        return bidsFromResponse(bidResponse, bidRequest.getImp());
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<Imp> imps) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(bid -> checkBid(bid.getImpid(), imps))
                .map(bid -> BidderBid.of(bid, BidType.banner, bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static boolean checkBid(String bidImpId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(bidImpId)) {
                return imp.getBanner() != null;
            }
        }
        throw new PreBidException(String.format("Unknown ad unit code '%s'", bidImpId));
    }
}
