package org.prebid.server.bidder.sovrn;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.User;
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
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.sovrn.ExtImpSovrn;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Sovrn {@link Bidder} implementation.
 */
public class SovrnBidder implements Bidder<BidRequest> {

    private static final String LJT_READER_COOKIE_NAME = "ljt_reader";

    private static final TypeReference<ExtPrebid<?, ExtImpSovrn>> SOVRN_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpSovrn>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SovrnBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        if (CollectionUtils.isEmpty(bidRequest.getImp())) {
            return Result.empty();
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> processedImps = new ArrayList<>();
        for (final Imp imp : bidRequest.getImp()) {
            try {
                processedImps.add(makeImp(imp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final BidRequest outgoingRequest = bidRequest.toBuilder().imp(processedImps).build();
        final String body = mapper.encode(outgoingRequest);

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .body(body)
                        .headers(headers(bidRequest))
                        .payload(outgoingRequest)
                        .build()),
                errors);
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private Imp makeImp(Imp imp) {
        if (imp.getXNative() != null || imp.getAudio() != null || imp.getVideo() != null) {
            throw new PreBidException(
                    String.format("Sovrn doesn't support audio, video, or native Imps. Ignoring Imp ID=%s",
                            imp.getId()));
        }

        final ExtImpSovrn sovrnExt = parseExtImpSovrn(imp);
        return imp.toBuilder()
                .bidfloor(resolveBidFloor(imp.getBidfloor(), sovrnExt.getBidfloor()))
                .tagid(ObjectUtils.defaultIfNull(sovrnExt.getTagid(), sovrnExt.getLegacyTagId()))
                .build();
    }

    private ExtImpSovrn parseExtImpSovrn(Imp imp) {
        if (imp.getExt() == null) {
            throw new PreBidException("Sovrn parameters section is missing");
        }

        try {
            return mapper.mapper().convertValue(imp.getExt(), SOVRN_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static BigDecimal resolveBidFloor(BigDecimal impBidFloor, BigDecimal extBidFloor) {
        return !isValidBidFloor(impBidFloor) && isValidBidFloor(extBidFloor) ? extBidFloor : impBidFloor;
    }

    private static boolean isValidBidFloor(BigDecimal bidFloor) {
        return bidFloor != null && bidFloor.compareTo(BigDecimal.ZERO) > 0;
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

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(updateBid(bid), BidType.banner, bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static Bid updateBid(Bid bid) {
        return bid.toBuilder()
                .adm(HttpUtil.decodeUrl(bid.getAdm()))
                .build();
    }
}
