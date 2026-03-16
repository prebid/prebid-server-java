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
        final ExtImpTrustx impExt = tryParseImpExt(imp);

        return impExt == null ? imp : imp.toBuilder()
                .ext(mapper.mapper().valueToTree(modifyImpExt(impExt)))
                .build();
    }

    private ExtImpTrustx tryParseImpExt(Imp imp) {
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

        return impExt.toBuilder()
                .gpid(adSlot != null ? adSlot : impExt.getGpid())
                .build();
    }

    private MultiMap makeHeaders(BidRequest request) {
        final Site site = request.getSite();
        final String referrer = ObjectUtil.getIfNotNull(site, Site::getRef);
        final String domain = ObjectUtil.getIfNotNull(site, Site::getDomain);

        final Device device = request.getDevice();
        final String ip = StringUtils.firstNonEmpty(
                ObjectUtil.getIfNotNull(device, Device::getIpv6),
                ObjectUtil.getIfNotNull(device, Device::getIp));
        final String userAgent = ObjectUtil.getIfNotNull(device, Device::getUa);

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
                .map(bid -> makeBid(bid, errors))
                .filter(Objects::nonNull)
                .toList();

        return Result.of(bidderBids, errors);
    }

    private BidderBid makeBid(Bid bid, List<BidderError> errors) {
        try {
            final BidType bidType = getBidType(bid);

            final ExtBidPrebidVideo videoInfo = (bidType == BidType.video) ? ExtBidPrebidVideo.of(
                    bid.getDur() != null && bid.getDur() > 0 ? bid.getDur() : null,
                    CollectionUtils.isNotEmpty(bid.getCat()) ? bid.getCat().getFirst() : null
            ) : null;

            final Bid modifiedBid = Optional.ofNullable(tryParseBidExt(bid.getExt()))
                    .map(TrustxBidder::modifyBidExt)
                    .map(mapper.mapper()::<ObjectNode>valueToTree)
                    .map(extBid -> bid.toBuilder().ext(extBid).build())
                    .orElse(bid);

            return BidderBid.builder()
                    .bid(modifiedBid)
                    .type(bidType)
                    .videoInfo(videoInfo)
                    .build();
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
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

    private ExtPrebid<ExtBidPrebid, ExtBidBidderTrustx> tryParseBidExt(ObjectNode bidExt) {
        try {
            return mapper.mapper().convertValue(bidExt, TRUSTX_BID_EXT_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ExtPrebid<ExtBidPrebid, ExtBidBidderTrustx> modifyBidExt(
            ExtPrebid<ExtBidPrebid, ExtBidBidderTrustx> extBid) {

        return Optional.ofNullable(extBid.getBidder())
                .map(ExtBidBidderTrustx::getTrustx)
                .map(ExtBidTrustx::getNetworkName)
                .filter(StringUtils::isNotEmpty)
                .map(networkName -> ExtBidPrebidMeta.builder().networkName(networkName).build())
                .map(extBidPrebidMeta -> modifyBidExtMeta(extBid, extBidPrebidMeta))
                .orElse(extBid);
    }

    private static ExtPrebid<ExtBidPrebid, ExtBidBidderTrustx> modifyBidExtMeta(
            ExtPrebid<ExtBidPrebid, ExtBidBidderTrustx> extBid, ExtBidPrebidMeta extBidPrebidMeta) {

        final ExtBidPrebid updatedExtBidPrebid = Optional.ofNullable(extBid.getPrebid())
                .map(ExtBidPrebid::toBuilder)
                .orElseGet(ExtBidPrebid::builder)
                .meta(extBidPrebidMeta)
                .build();

        return ExtPrebid.of(updatedExtBidPrebid, extBid.getBidder());
    }
}
