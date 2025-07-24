package org.prebid.server.bidder.thetradedesk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.proto.openrtb.ext.request.thetradedesk.ExtImpTheTradeDesk;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class TheTradeDeskBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpTheTradeDesk>> TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String SUPPLY_ID_MACRO = "{{SupplyId}}";
    private static final Pattern SUPPLY_ID_PATTERN = Pattern.compile("([a-z]+)$");

    private final String endpointUrl;
    private final String supplyId;
    private final JacksonMapper mapper;

    public TheTradeDeskBidder(String endpointUrl, JacksonMapper mapper, String supplyId) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.supplyId = validateSupplyId(supplyId);
        this.mapper = Objects.requireNonNull(mapper);
    }

    private static String validateSupplyId(String supplyId) {
        if (StringUtils.isBlank(supplyId) || SUPPLY_ID_PATTERN.matcher(supplyId).matches()) {
            return supplyId;
        }

        throw new IllegalArgumentException("SupplyId must be a simple string provided by TheTradeDesk");
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> modifiedImps = new ArrayList<>();

        String publisherId = null;
        String sourceSupplyId = null;
        for (Imp imp : request.getImp()) {
            try {
                final ExtImpTheTradeDesk extImp = parseImpExt(imp);

                final String extImpPublisherId = extImp.getPublisherId();
                publisherId = publisherId == null && StringUtils.isNotBlank(extImpPublisherId)
                        ? extImpPublisherId
                        : publisherId;

                final String extImpSourceSupplyId = extImp.getSupplySourceId();
                sourceSupplyId = sourceSupplyId == null && StringUtils.isNotBlank(extImpSourceSupplyId)
                        ? extImpSourceSupplyId
                        : sourceSupplyId;

                modifiedImps.add(modifyImp(imp));
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
        }

        if (StringUtils.isBlank(sourceSupplyId) && StringUtils.isBlank(supplyId)) {
            return Result.withError(
                BidderError.badInput("Either supplySourceId or a default endpoint must be provided"));
        }

        final BidRequest outgoingRequest = modifyRequest(request, modifiedImps, publisherId);
        final HttpRequest<BidRequest> httpRequest = BidderUtil.defaultRequest(
                outgoingRequest,
                resolveEndpoint(sourceSupplyId),
                mapper);

        return Result.withValue(httpRequest);
    }

    private ExtImpTheTradeDesk parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static Imp modifyImp(Imp imp) {
        final Banner banner = imp.getBanner();

        if (banner != null && CollectionUtils.isNotEmpty(banner.getFormat())) {
            final Format format = banner.getFormat().getFirst();
            return imp.toBuilder()
                    .banner(banner.toBuilder().w(format.getW()).h(format.getH()).build())
                    .build();
        }

        return imp;
    }

    private static BidRequest modifyRequest(BidRequest request, List<Imp> modifiedImps, String publisherId) {
        return request.toBuilder()
                .imp(modifiedImps)
                .site(modifySite(request, publisherId))
                .app(modifyApp(request, publisherId))
                .build();
    }

    private static Site modifySite(BidRequest request, String publisherId) {
        final Site site = request.getSite();
        if (site == null) {
            return null;
        }

        return site.toBuilder()
                .publisher(modifyPublisher(site.getPublisher(), publisherId))
                .build();
    }

    private static Publisher modifyPublisher(Publisher publisher, String publisherId) {
        if (publisher == null) {
            return Publisher.builder().id(publisherId).build();
        }

        return publisher.toBuilder()
                .id(StringUtils.isNotBlank(publisherId) ? publisherId : publisher.getId())
                .build();
    }

    private static App modifyApp(BidRequest request, String publisherId) {
        final Site site = request.getSite();
        final App app = request.getApp();

        if (site != null) {
            return app;
        }

        if (app == null) {
            return null;
        }

        return app.toBuilder()
                .publisher(modifyPublisher(app.getPublisher(), publisherId))
                .build();
    }

    private String resolveEndpoint(String sourceSupplyId) {
        return endpointUrl.replace(
                SUPPLY_ID_MACRO,
                HttpUtil.encodeUrl(StringUtils.defaultString(ObjectUtils.defaultIfNull(sourceSupplyId, supplyId))));
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
                .map(bid -> BidderBid.of(bid, getBidType(bid), bidResponse.getCur()))
                .toList();
    }

    private static BidType getBidType(Bid bid) {
        return switch (bid.getMtype()) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 4 -> BidType.xNative;
            case null, default -> throw new PreBidException("unsupported mtype: %s".formatted(bid.getMtype()));
        };
    }
}
