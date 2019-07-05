package org.prebid.server.bidder.sovrn;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Cookie;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.OpenrtbAdapter;
import org.prebid.server.bidder.model.AdUnitBidWithParams;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.bidder.sovrn.proto.SovrnParams;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.MediaType;
import org.prebid.server.util.HttpUtil;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Sovrn {@link Adapter} implementation.
 */
public class SovrnAdapter extends OpenrtbAdapter {

    private static final String LJT_READER_COOKIE_NAME = "ljt_reader";

    private final String endpointUrl;

    public SovrnAdapter(String cookieFamilyName, String endpointUrl) {
        super(cookieFamilyName);
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    @Override
    public List<AdapterHttpRequest<BidRequest>> makeHttpRequests(AdapterRequest adapterRequest,
                                                                 PreBidRequestContext preBidRequestContext) {
        final BidRequest bidRequest = createBidRequest(adapterRequest, preBidRequestContext);
        final AdapterHttpRequest<BidRequest> httpRequest = AdapterHttpRequest.of(
                HttpMethod.POST,
                endpointUrl,
                createBidRequest(adapterRequest, preBidRequestContext),
                headers(bidRequest));

        return Collections.singletonList(httpRequest);
    }

    private BidRequest createBidRequest(AdapterRequest adapterRequest, PreBidRequestContext preBidRequestContext) {
        final List<AdUnitBid> adUnitBids = adapterRequest.getAdUnitBids();

        final List<AdUnitBidWithParams<SovrnParams>> adUnitBidsWithParams = createAdUnitBidsWithParams(adUnitBids);
        final List<Imp> imps = makeImps(adUnitBidsWithParams, preBidRequestContext);
        validateImps(imps);

        final PreBidRequest preBidRequest = preBidRequestContext.getPreBidRequest();
        return BidRequest.builder()
                .id(preBidRequest.getTid())
                .at(1)
                .tmax(preBidRequest.getTimeoutMillis())
                .imp(imps)
                .app(preBidRequest.getApp())
                .site(makeSite(preBidRequestContext))
                .device(deviceBuilder(preBidRequestContext).build())
                .user(makeUser(preBidRequestContext))
                .source(makeSource(preBidRequestContext))
                .regs(preBidRequest.getRegs())
                .build();
    }

    private static List<AdUnitBidWithParams<SovrnParams>> createAdUnitBidsWithParams(List<AdUnitBid> adUnitBids) {
        return adUnitBids.stream()
                .map(adUnitBid -> AdUnitBidWithParams.of(adUnitBid, parseAndValidateParams(adUnitBid)))
                .collect(Collectors.toList());
    }

    private static SovrnParams parseAndValidateParams(AdUnitBid adUnitBid) {
        final ObjectNode paramsNode = adUnitBid.getParams();
        if (paramsNode == null) {
            throw new PreBidException("Sovrn params section is missing");
        }

        try {
            return Json.mapper.convertValue(paramsNode, SovrnParams.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e.getCause());
        }
    }

    private static List<Imp> makeImps(List<AdUnitBidWithParams<SovrnParams>> adUnitBidsWithParams,
                                      PreBidRequestContext preBidRequestContext) {
        return adUnitBidsWithParams.stream()
                .filter(SovrnAdapter::isSupportedMediaType)
                .map(adUnitBidWithParams -> makeImp(adUnitBidWithParams, preBidRequestContext))
                .collect(Collectors.toList());
    }

    private static boolean isSupportedMediaType(AdUnitBidWithParams<SovrnParams> sovrnAdUnitBidWithParams) {
        return sovrnAdUnitBidWithParams.getAdUnitBid().getMediaTypes().contains(MediaType.banner);
    }

    private static Imp makeImp(AdUnitBidWithParams<SovrnParams> adUnitBidWithParams,
                               PreBidRequestContext preBidRequestContext) {
        final AdUnitBid adUnitBid = adUnitBidWithParams.getAdUnitBid();
        final SovrnParams params = adUnitBidWithParams.getParams();

        return Imp.builder()
                .id(adUnitBid.getAdUnitCode())
                .banner(bannerBuilder(adUnitBid).build())
                .instl(adUnitBid.getInstl())
                .secure(preBidRequestContext.getSecure())
                .tagid(params.getTagId())
                .build();
    }

    private MultiMap headers(BidRequest bidRequest) {
        final MultiMap headers = headers();

        final Device device = bidRequest.getDevice();
        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER.toString(), device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ACCEPT_LANGUAGE_HEADER.toString(),
                    device.getLanguage());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER.toString(), device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.DNT_HEADER.toString(),
                    Objects.toString(device.getDnt(), null));
        }

        final User user = bidRequest.getUser();
        final String buyeruid = user != null ? StringUtils.trimToNull(user.getBuyeruid()) : null;
        if (buyeruid != null) {
            headers.add(HttpUtil.COOKIE_HEADER.toString(), Cookie.cookie(LJT_READER_COOKIE_NAME, buyeruid).encode());
        }
        return headers;
    }

    @Override
    public List<Bid.BidBuilder> extractBids(AdapterRequest adapterRequest,
                                            ExchangeCall<BidRequest, BidResponse> exchangeCall) {
        return responseBidStream(exchangeCall.getResponse())
                .sorted(Comparator.comparingDouble(bid -> bid.getPrice() != null ? bid.getPrice().doubleValue() : 0d))
                .map(bid -> toBidBuilder(bid, adapterRequest))
                .collect(Collectors.toList());
    }

    private static Bid.BidBuilder toBidBuilder(com.iab.openrtb.response.Bid bid, AdapterRequest adapterRequest) {
        final AdUnitBid adUnitBid = lookupBid(adapterRequest.getAdUnitBids(), bid.getImpid());
        return Bid.builder()
                .bidId(adUnitBid.getBidId())
                .code(bid.getImpid())
                .bidder(adUnitBid.getBidderCode())
                .price(bid.getPrice())
                .adm(HttpUtil.decodeUrl(bid.getAdm()))
                .creativeId(bid.getCrid())
                .width(bid.getW())
                .height(bid.getH())
                .dealId(bid.getDealid())
                .nurl(bid.getNurl());
    }
}
