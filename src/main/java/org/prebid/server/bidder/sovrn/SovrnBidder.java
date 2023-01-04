package org.prebid.server.bidder.sovrn;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
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
import org.prebid.server.proto.openrtb.ext.request.sovrn.ExtImpSovrn;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SovrnBidder implements Bidder<BidRequest> {

    private static final String LJT_READER_COOKIE_NAME = "ljt_reader";

    private static final TypeReference<ExtPrebid<?, ExtImpSovrn>> SOVRN_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SovrnBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> processedImps = new ArrayList<>();

        for (final Imp imp : bidRequest.getImp()) {
            try {
                validateImpVideo(imp.getVideo());
                processedImps.add(makeImp(imp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final BidRequest outgoingRequest = bidRequest.toBuilder().imp(processedImps).build();

        return makeHttpRequest(outgoingRequest, errors);
    }

    private static void validateImpVideo(Video video) {
        if (video != null) {
            if (video.getMimes() == null
                    || BidderUtil.isNullOrZero(video.getMinduration())
                    || BidderUtil.isNullOrZero(video.getMaxduration())
                    || video.getProtocols() == null) {
                throw new PreBidException("Missing required video parameter");
            }
        }
    }

    private Imp makeImp(Imp imp) {
        final ExtImpSovrn sovrnExt = parseExtImpSovrn(imp);

        return imp.toBuilder()
                .bidfloor(resolveBidFloor(imp.getBidfloor(), sovrnExt.getBidfloor()))
                .tagid(resolveTagId(sovrnExt))
                .build();
    }

    private ExtImpSovrn parseExtImpSovrn(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), SOVRN_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static BigDecimal resolveBidFloor(BigDecimal impBidFloor, BigDecimal extBidFloor) {
        return !BidderUtil.isValidPrice(impBidFloor) && BidderUtil.isValidPrice(extBidFloor)
                ? extBidFloor
                : impBidFloor;
    }

    private String resolveTagId(ExtImpSovrn sovrnExt) {
        final String tagId = ObjectUtils.defaultIfNull(sovrnExt.getTagid(), sovrnExt.getLegacyTagId());
        if (StringUtils.isEmpty(tagId)) {
            throw new PreBidException("Missing required parameter 'tagid'");
        }
        return tagId;
    }

    private Result<List<HttpRequest<BidRequest>>> makeHttpRequest(BidRequest bidRequest,
                                                                  List<BidderError> errors) {

        return Result.of(Collections.singletonList(
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(endpointUrl)
                                .body(mapper.encodeToBytes(bidRequest))
                                .headers(headers(bidRequest))
                                .payload(bidRequest)
                                .build()),
                errors);
    }

    private static MultiMap headers(BidRequest bidRequest) {
        final MultiMap headers = HttpUtil.headers();

        final Device device = bidRequest.getDevice();
        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ACCEPT_LANGUAGE_HEADER, device.getLanguage());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.DNT_HEADER, Objects.toString(device.getDnt(), null));
        }

        final User user = bidRequest.getUser();
        final String buyeruid = user != null ? StringUtils.trimToNull(user.getBuyeruid()) : null;
        if (buyeruid != null) {
            headers.add(HttpUtil.COOKIE_HEADER, Cookie.cookie(LJT_READER_COOKIE_NAME, buyeruid).encode());
        }

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> bidderErrors = new ArrayList<>();
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final BidRequest request = httpCall.getRequest().getPayload();

            return Result.of(extractBids(bidResponse, request, bidderErrors), bidderErrors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse,
                                               BidRequest bidRequest,
                                               List<BidderError> bidderErrors) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse, bidRequest, bidderErrors);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse,
                                                    BidRequest bidRequest,
                                                    List<BidderError> bidderErrors) {

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> makeBidderBid(
                        bid,
                        resolveBidType(bid.getImpid(), bidRequest.getImp(), bidderErrors),
                        bidResponse.getCur()))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid makeBidderBid(Bid bid, BidType bidType, String cur) {
        if (bidType == null) {
            return null;
        }

        final Bid updatedBid = bid.toBuilder().adm(HttpUtil.decodeUrl(bid.getAdm())).build();
        return BidderBid.of(updatedBid, bidType, cur);
    }

    private static BidType resolveBidType(String impId, List<Imp> imps, List<BidderError> bidderErrors) {
        for (Imp imp : imps) {
            final boolean matchedImpId = impId.equals(imp.getId());
            if (matchedImpId && imp.getVideo() != null) {
                return BidType.video;
            } else if (matchedImpId) {
                return BidType.banner;
            }
        }

        bidderErrors.add(
                BidderError.badInput("Imp ID " + impId + " in bid didn't match with any imp in the original request"));
        return null;
    }
}
