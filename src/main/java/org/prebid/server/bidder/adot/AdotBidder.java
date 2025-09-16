package org.prebid.server.bidder.adot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.adot.model.AdotBidExt;
import org.prebid.server.bidder.adot.model.AdotExtAdot;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adot.ExtImpAdot;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AdotBidder implements Bidder<BidRequest> {

    private static final List<BidType> ALLOWED_BID_TYPES = Arrays.asList(BidType.banner, BidType.video,
            BidType.xNative);
    private static final String PRICE_MACRO = "${AUCTION_PRICE}";
    private static final String PUBLISHER_MACRO = "{{PUBLISHER_PATH}}";
    private static final TypeReference<ExtPrebid<?, ExtImpAdot>> ADOT_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AdotBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();

        final Imp firstImp = bidRequest.getImp().getFirst();
        final String publisherPath = StringUtils.defaultString(
                ObjectUtil.getIfNotNull(parseImpExt(firstImp), ExtImpAdot::getPublisherPath));

        return Result.of(Collections.singletonList(
                BidderUtil.defaultRequest(bidRequest, resolveEndpointUrl(publisherPath), mapper)),
                errors);
    }

    private String resolveEndpointUrl(String publisherPath) {
        return StringUtils.replace(endpointUrl, PUBLISHER_MACRO, publisherPath);
    }

    private ExtImpAdot parseImpExt(Imp firstImp) {
        try {
            final ExtPrebid<?, ExtImpAdot> extImpAdot =
                    mapper.mapper().convertValue(firstImp.getExt(), ADOT_EXT_TYPE_REFERENCE);
            return extImpAdot != null ? extImpAdot.getBidder() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> createBidderBid(bid, bidResponse))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid createBidderBid(Bid bid, BidResponse bidResponse) {
        try {
            return BidderBid.of(resolveMacros(bid), getBidType(bid), bidResponse.getCur());
        } catch (PreBidException e) {
            return null;
        }
    }

    private static Bid resolveMacros(Bid bid) {
        final BigDecimal price = bid.getPrice();
        final String priceAsString = price != null ? price.toPlainString() : "0";

        return bid.toBuilder()
                .nurl(StringUtils.replace(bid.getNurl(), PRICE_MACRO, priceAsString))
                .adm(StringUtils.replace(bid.getAdm(), PRICE_MACRO, priceAsString))
                .build();
    }

    private BidType getBidType(Bid bid) {
        final String bidExtType = parseBidExtType(bid);
        return ALLOWED_BID_TYPES.stream()
                .filter(bidType -> bidType.getName().equals(bidExtType))
                .findFirst()
                .orElseThrow(() -> new PreBidException(
                        "Wrong Adot bid ext in bid with id : " + bid.getId()));
    }

    private String parseBidExtType(Bid bid) {
        try {
            final AdotBidExt adotBidExt = mapper.mapper().convertValue(bid.getExt(), AdotBidExt.class);
            final AdotExtAdot adotExtAdot = adotBidExt != null ? adotBidExt.getAdot() : null;
            final String mediaType = adotExtAdot != null ? adotExtAdot.getMediaType() : null;

            if (StringUtils.isBlank(mediaType)) {
                throw new IllegalArgumentException();
            }

            return mediaType;
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Wrong Adot bid ext in bid with id : " + bid.getId());
        }
    }
}
