package org.prebid.server.bidder.trustx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.prebid.server.proto.openrtb.ext.request.trustx.ExtBidBidderTrustx;
import org.prebid.server.proto.openrtb.ext.request.trustx.ExtBidTrustx;
import org.prebid.server.proto.openrtb.ext.request.trustx.ExtImpTrustx;
import org.prebid.server.proto.openrtb.ext.request.trustx.ExtImpTrustxData;
import org.prebid.server.proto.openrtb.ext.request.trustx.ExtImpTrustxDataAdServer;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class TrustxBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<ExtBidPrebid, ExtBidBidderTrustx>> TRUSTX_BID_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String OPENRTB_VERSION = "2.6";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public TrustxBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> updatedImps = request.getImp().stream().map(this::modifyImp).toList();
        final BidRequest updatedRequest = request.toBuilder().imp(updatedImps).build();
        return Result.withValue(BidderUtil.defaultRequest(updatedRequest, makeHeaders(request), endpointUrl, mapper));
    }

    private Imp modifyImp(Imp imp) {
        final ExtImpTrustx impExt = parseImpExt(imp);

        return impExt != null
                ? imp.toBuilder().ext(mapper.mapper().valueToTree(modifyImpExt(impExt))).build()
                : imp;
    }

    private ExtImpTrustx parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ExtImpTrustx.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ExtImpTrustx modifyImpExt(ExtImpTrustx impExt) {
        final String adSlot = Optional.ofNullable(impExt.getData())
                .map(ExtImpTrustxData::getAdServer)
                .map(ExtImpTrustxDataAdServer::getAdSlot)
                .filter(StringUtils::isNotEmpty)
                .orElse(null);

        return adSlot != null ? impExt.toBuilder().gpid(adSlot).build() : impExt;
    }

    private MultiMap makeHeaders(BidRequest request) {
        final Site site = request.getSite();
        final String referrer = ObjectUtil.getIfNotNull(site, Site::getRef);
        final String domain = ObjectUtil.getIfNotNull(site, Site::getDomain);

        final Device device = request.getDevice();
        final String ip = StringUtils.firstNonEmpty(
                device != null ? device.getIpv6() : null,
                device != null ? device.getIp() : null);
        final String userAgent = device != null ? device.getUa() : null;

        final MultiMap headers = HttpUtil.headers();

        headers.add(HttpUtil.X_OPENRTB_VERSION_HEADER, OPENRTB_VERSION);
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.REFERER_HEADER, referrer);
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ORIGIN_HEADER, domain);
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, ip);
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, userAgent);

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = decodeBodyToBidResponse(httpCall);
            return bidsFromResponse(bidResponse);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private BidResponse decodeBodyToBidResponse(BidderCall<BidRequest> httpCall) {
        try {
            return mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException("Failed to parse response as BidResponse: " + e.getMessage());
        }
    }

    private Result<List<BidderBid>> bidsFromResponse(BidResponse bidResponse) {
        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bidderBids = Stream.ofNullable(bidResponse)
                .map(BidResponse::getSeatbid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, errors))
                .filter(Objects::nonNull)
                .toList();

        return Result.of(bidderBids, errors);
    }

    private BidderBid makeBidderBid(Bid bid, List<BidderError> errors) {
        final BidType bidType;
        try {
            bidType = getBidType(bid);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }

        return BidderBid.builder()
                .bid(modifyBid(bid))
                .type(bidType)
                .videoInfo(bidType == BidType.video ? makeExtBidPrebidVideo(bid) : null)
                .build();
    }

    private Bid modifyBid(Bid bid) {
        final ExtPrebid<ExtBidPrebid, ExtBidBidderTrustx> ext = parseBidExt(bid.getExt());
        if (ext == null) {
            return bid;
        }

        final ExtBidBidderTrustx extBidder = ext.getBidder();
        final String networkName = Optional.ofNullable(extBidder)
                .map(ExtBidBidderTrustx::getTrustx)
                .map(ExtBidTrustx::getNetworkName)
                .filter(StringUtils::isNotEmpty)
                .orElse(null);
        if (networkName == null) {
            return bid;
        }

        final ExtBidPrebid modifiedExtPrebid = Optional.ofNullable(ext.getPrebid())
                .map(ExtBidPrebid::toBuilder)
                .orElseGet(ExtBidPrebid::builder)
                .meta(ExtBidPrebidMeta.builder().networkName(networkName).build())
                .build();
        final ObjectNode modifiedExt = mapper.mapper().valueToTree(ExtPrebid.of(modifiedExtPrebid, extBidder));

        return bid.toBuilder().ext(modifiedExt).build();
    }

    private ExtPrebid<ExtBidPrebid, ExtBidBidderTrustx> parseBidExt(ObjectNode bidExt) {
        try {
            return mapper.mapper().convertValue(bidExt, TRUSTX_BID_EXT_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static BidType getBidType(Bid bid) {
        final Integer markupType = bid.getMtype();
        if (markupType == null) {
            throw new PreBidException("Missing MType for bid: " + bid.getId());
        }

        return switch (markupType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            default -> throw new PreBidException("Unsupported MType: %d".formatted(markupType));
        };
    }

    private static ExtBidPrebidVideo makeExtBidPrebidVideo(Bid bid) {
        final Integer dur = bid.getDur();
        final List<String> cat = bid.getCat();

        return ExtBidPrebidVideo.of(
                dur != null && dur > 0 ? dur : null,
                CollectionUtils.isNotEmpty(cat) ? cat.getFirst() : null);
    }
}
