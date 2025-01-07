package org.prebid.server.bidder.metax;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.proto.openrtb.ext.request.metax.ExtImpMetax;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class MetaxBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpMetax>> METAX_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String PUBLISHER_ID_MACRO = "{{publisherId}}";
    private static final String AD_UNIT_MACRO = "{{adUnit}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public MetaxBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpMetax extImpMetax = parseImpExt(imp);
                httpRequests.add(BidderUtil.defaultRequest(prepareBidRequest(request, imp),
                        resolveEndpoint(extImpMetax),
                        mapper));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(httpRequests, errors);
    }

    private ExtImpMetax parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), METAX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private static BidRequest prepareBidRequest(BidRequest bidRequest, Imp imp) {
        return bidRequest.toBuilder()
                .imp(Collections.singletonList(modifyImp(imp)))
                .build();
    }

    private static Imp modifyImp(Imp imp) {
        final Banner banner = imp.getBanner();
        final Integer width = banner != null ? banner.getW() : null;
        final Integer height = banner != null ? banner.getH() : null;
        if (width != null && height != null) {
            return imp;
        }

        final List<Format> formats = banner != null ? banner.getFormat() : null;
        if (CollectionUtils.isEmpty(formats)) {
            return imp;
        }

        final Format firstFormat = formats.getFirst();
        return imp.toBuilder()
                .banner(banner.toBuilder()
                        .w(Optional.ofNullable(firstFormat).map(Format::getW).orElse(0))
                        .h(Optional.ofNullable(firstFormat).map(Format::getH).orElse(0))
                        .build())
                .build();
    }

    private String resolveEndpoint(ExtImpMetax extImpMetax) {
        final String publisherIdAsString = Optional.ofNullable(extImpMetax.getPublisherId())
                .map(Object::toString)
                .orElse(StringUtils.EMPTY);
        final String adUnitAsString = Optional.ofNullable(extImpMetax.getAdUnit())
                .map(Object::toString)
                .orElse(StringUtils.EMPTY);

        return endpointUrl
                .replace(PUBLISHER_ID_MACRO, publisherIdAsString)
                .replace(AD_UNIT_MACRO, adUnitAsString);
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

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid).filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.builder()
                        .bid(bid)
                        .type(getBidType(bid))
                        .bidCurrency(bidResponse.getCur())
                        .videoInfo(videoInfo(bid))
                        .build())

                .toList();
    }

    private static BidType getBidType(Bid bid) {
        final Integer markupType = bid.getMtype();
        if (markupType == null) {
            throw new PreBidException("Missing MType for bid: " + bid.getId());
        }

        return switch (markupType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 3 -> BidType.audio;
            case 4 -> BidType.xNative;
            default -> throw new PreBidException("Unsupported MType: %s"
                    .formatted(bid.getImpid()));
        };
    }

    private static ExtBidPrebidVideo videoInfo(Bid bid) {
        final List<String> cat = bid.getCat();
        final Integer duration = bid.getDur();

        final boolean catNotEmpty = CollectionUtils.isNotEmpty(cat);
        final boolean durationValid = duration != null && duration > 0;
        return catNotEmpty || durationValid
                ? ExtBidPrebidVideo.of(
                durationValid ? duration : null,
                catNotEmpty ? cat.getFirst() : null)
                : null;
    }
}
