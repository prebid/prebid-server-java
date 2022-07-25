package org.prebid.server.bidder.telaria;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.telaria.model.TelariaRequestExt;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.telaria.ExtImpOutTelaria;
import org.prebid.server.proto.openrtb.ext.request.telaria.ExtImpTelaria;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

public class TelariaBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpTelaria>> TELARIA_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public TelariaBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        try {
            validateImp(bidRequest.getImp());
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final String publisherId = getPublisherId(bidRequest);
        String seatCode;
        ExtImpTelaria extImp;
        Imp modifyImp = bidRequest.getImp().get(0);

        try {
            extImp = parseImpExt(modifyImp);
            seatCode = extImp.getSeatCode();
            modifyImp = updateImp(modifyImp, extImp, publisherId);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }
        final BidRequest outgoingRequest = updateBidRequest(bidRequest, extImp, seatCode, modifyImp);
        return Result.withValue(makeHttpRequest(outgoingRequest));
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest bidRequest) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(headers(bidRequest))
                .payload(bidRequest)
                .body(mapper.encodeToBytes(bidRequest))
                .build();
    }

    private BidRequest updateBidRequest(BidRequest bidRequest, ExtImpTelaria extImp, String seatCode, Imp modifyImp) {
        final BidRequest.BidRequestBuilder bidRequestBuilder = bidRequest.toBuilder();

        if (bidRequest.getSite() != null) {
            bidRequestBuilder.site(modifySite(bidRequest.getSite(), seatCode));
        } else if (bidRequest.getApp() != null) {
            bidRequestBuilder.app(modifyApp(bidRequest.getApp(), seatCode));
        }

        return bidRequestBuilder
                .ext(modifyExt(extImp))
                .imp(Collections.singletonList(modifyImp))
                .build();
    }

    private static void validateImp(List<Imp> imps) {
        boolean hasVideoObject = false;
        for (Imp imp : imps) {
            if (imp.getBanner() != null) {
                throw new PreBidException("Telaria: Banner not supported");
            }
            hasVideoObject = hasVideoObject || imp.getVideo() != null;
        }

        if (!hasVideoObject) {
            throw new PreBidException("Telaria: Only Supports Video");
        }
    }

    private static String getPublisherId(BidRequest bidRequest) {
        if (bidRequest.getSite() != null && bidRequest.getSite().getPublisher() != null) {
            return bidRequest.getSite().getPublisher().getId();
        } else if (bidRequest.getApp() != null && bidRequest.getApp().getPublisher() != null) {
            return bidRequest.getApp().getPublisher().getId();
        }
        return "";
    }

    private ExtImpTelaria parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TELARIA_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private Imp updateImp(Imp imp, ExtImpTelaria extImp, String publisherId) {
        if (StringUtils.isBlank(extImp.getSeatCode())) {
            throw new PreBidException("Telaria: Seat Code required");
        }
        return imp.toBuilder()
                .tagid(extImp.getAdCode())
                .ext(mapper.mapper().valueToTree(ExtImpOutTelaria.of(imp.getTagid(), publisherId)))
                .build();
    }

    private ExtRequest modifyExt(ExtImpTelaria extImp) {
        return extImp != null
                ? mapper.fillExtension(ExtRequest.empty(), TelariaRequestExt.of(extImp.getExtra()))
                : null;
    }

    private static Site modifySite(Site site, String seatCode) {
        return site.toBuilder().publisher(createPublisher(site.getPublisher(), seatCode)).build();
    }

    private static App modifyApp(App app, String seatCode) {
        return app.toBuilder().publisher(createPublisher(app.getPublisher(), seatCode)).build();
    }

    private static Publisher createPublisher(Publisher publisher, String seatCode) {
        return publisher != null
                ? publisher.toBuilder().id(seatCode).build()
                : Publisher.builder().id(seatCode).build();
    }

    private static MultiMap headers(BidRequest bidRequest) {
        final MultiMap headers = HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");

        final Device device = bidRequest.getDevice();
        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ACCEPT_LANGUAGE_HEADER, device.getLanguage());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.DNT_HEADER, Objects.toString(device.getDnt(), null));
        }

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse, bidRequest));
        } catch (PreBidException | DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, bidRequest);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse, BidRequest bidRequest) {
        final SeatBid firstSeatBid = bidResponse.getSeatbid().get(0);
        final List<Bid> bids = firstSeatBid.getBid();
        final List<Imp> imps = bidRequest.getImp();

        if (CollectionUtils.isEmpty(bids)) {
            return Collections.emptyList();
        }

        return IntStream.range(0, bids.size())
                .filter(i -> bids.get(i) != null)
                .filter(i -> i < imps.size())
                .mapToObj(i -> makeBidderBid(bids.get(i), bidResponse, imps.get(i)))
                .toList();
    }

    private static BidderBid makeBidderBid(Bid bid, BidResponse bidResponse, Imp imp) {
        final Bid modifyBid = bid.toBuilder().impid(imp.getId()).build();
        return BidderBid.of(modifyBid, BidType.video, bidResponse.getCur());
    }
}
