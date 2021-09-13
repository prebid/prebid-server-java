package org.prebid.server.bidder.adview;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
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
import org.prebid.server.proto.openrtb.ext.request.adview.ExtImpAdview;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AdviewBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdview>> ADVIEW_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAdview>>() {
            };
    private static final String ACCOUNT_ID_MACRO = "{{AccountId}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AdviewBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final Imp firstImp = request.getImp().get(0);
        final ExtImpAdview extImpAdview;

        try {
            extImpAdview = mapper.mapper().convertValue(firstImp.getExt(), ADVIEW_EXT_TYPE_REFERENCE).getBidder();
        } catch (DecodeException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final BidRequest modifiedRequest = modifyRequest(request, extImpAdview.getMasterTagId());
        return Result.withValue(makeHttpRequest(modifiedRequest, extImpAdview.getAccountId()));
    }

    private static BidRequest modifyRequest(BidRequest bidRequest, String masterTagId) {
        return bidRequest.toBuilder()
                .imp(modifyImps(bidRequest.getImp(), masterTagId))
                .build();
    }

    private static List<Imp> modifyImps(List<Imp> imps, String masterTagId) {
        final List<Imp> modifiedImps = new ArrayList<>(imps);
        modifiedImps.set(0, modifyImp(imps.get(0), masterTagId));
        return modifiedImps;
    }

    private static Imp modifyImp(Imp imp, String masterTagId) {
        return imp.toBuilder()
                .tagid(masterTagId)
                .banner(resolveBanner(imp.getBanner()))
                .build();
    }

    private static Banner resolveBanner(Banner banner) {
        final List<Format> formats = banner.getFormat();
        if (CollectionUtils.isNotEmpty(formats)) {
            final Format firstFormat = formats.get(0);
            return firstFormat != null
                    ? banner.toBuilder().w(firstFormat.getW()).h(firstFormat.getH()).build()
                    : banner;
        }
        return banner;
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request, String accountId) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(resolveEndpoint(accountId))
                .headers(HttpUtil.headers())
                .body(mapper.encode(request))
                .payload(request)
                .build();
    }

    private String resolveEndpoint(String accountId) {
        return endpointUrl.replace(ACCOUNT_ID_MACRO, HttpUtil.encodeUrl(accountId));
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
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
                .map(bid -> BidderBid.of(bid, getBidMediaType(bid.getImpid(), bidRequest.getImp()),
                        bidResponse.getCur()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private BidType getBidMediaType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                }
            }
        }
        return BidType.banner;
    }
}
