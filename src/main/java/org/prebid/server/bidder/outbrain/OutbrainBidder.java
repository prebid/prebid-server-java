package org.prebid.server.bidder.outbrain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.EventTracker;
import com.iab.openrtb.response.Response;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
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
import org.prebid.server.proto.openrtb.ext.request.outbrains.ExtImpOutbrain;
import org.prebid.server.proto.openrtb.ext.request.outbrains.ExtImpOutbrainPublisher;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class OutbrainBidder implements Bidder<BidRequest> {

    private static final int IMAGE_TRACKER_METHOD = 1;
    private static final int JS_TRACKER_METHOD = 2;
    private static final int EVENT_TYPE_IMPRESSION = 1;

    private static final TypeReference<ExtPrebid<?, ExtImpOutbrain>> OUTBRAIN_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpOutbrain>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public OutbrainBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> modifiedImps = new ArrayList<>();

        ExtImpOutbrain extImpOutbrain = null;
        for (Imp imp : request.getImp()) {
            try {
                extImpOutbrain = parseImpExt(imp);
                modifiedImps.add(modifyImp(imp, extImpOutbrain.getTagid()));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (!errors.isEmpty()) {
            return Result.withErrors(errors);
        }

        final BidRequest updatedRequest = updateBidRequest(request, modifiedImps, extImpOutbrain);

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .body(mapper.encode(updatedRequest))
                        .headers(HttpUtil.headers())
                        .payload(updatedRequest)
                        .build()), errors);
    }

    private ExtImpOutbrain parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), OUTBRAIN_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("Impression id=%s, has invalid Ext", imp.getId()));
        }
    }

    private static Imp modifyImp(Imp imp, String tagId) {
        return StringUtils.isNotEmpty(tagId)
                ? imp.toBuilder().tagid(tagId).build()
                : imp;
    }

    private static BidRequest updateBidRequest(BidRequest bidRequest, List<Imp> imps, ExtImpOutbrain extImpOutbrain) {
        final BidRequest.BidRequestBuilder bidRequestBuilder = bidRequest.toBuilder();
        final Publisher publisher = createPublisher(extImpOutbrain.getPublisher());

        final Site site = bidRequest.getSite();
        final App app = bidRequest.getApp();
        if (site != null) {
            bidRequestBuilder.site(updateSite(site, publisher));
        } else if (app != null) {
            bidRequestBuilder.app(updateApp(app, publisher));
        }

        final List<String> bcat = extImpOutbrain.getBcat();
        if (CollectionUtils.isNotEmpty(bcat)) {
            bidRequestBuilder.bcat(bcat);
        }

        final List<String> badv = extImpOutbrain.getBadv();
        if (CollectionUtils.isNotEmpty(badv)) {
            bidRequestBuilder.badv(badv);
        }

        return bidRequestBuilder.imp(imps).build();
    }

    private static Publisher createPublisher(ExtImpOutbrainPublisher extImpPublisher) {
        return Publisher.builder()
                .id(extImpPublisher.getId())
                .name(extImpPublisher.getName())
                .domain(extImpPublisher.getDomain())
                .build();
    }

    private static Site updateSite(Site site, Publisher publisher) {
        return site.toBuilder().publisher(publisher).build();
    }

    private static App updateApp(App app, Publisher publisher) {
        return app.toBuilder().publisher(publisher).build();
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();

        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse, errors);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse, List<BidderError> errors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> createBidderBid(bid, bidRequest.getImp(), bidResponse.getCur(), errors))
                .collect(Collectors.toList());
    }

    private BidderBid createBidderBid(Bid bid, List<Imp> requestImps, String cur, List<BidderError> errors) {
        final BidType bidType = getBidType(bid.getImpid(), requestImps);
        return BidderBid.of(updateBid(bid, bidType, errors), bidType, cur);
    }

    private Bid updateBid(Bid bid, BidType bidType, List<BidderError> errors) {
        final String bidAdm = bid.getAdm();
        final String resolvedAdm = bidType.equals(BidType.xNative) && StringUtils.isNotEmpty(bidAdm)
                ? resolveBidAdm(bidAdm, errors)
                : null;
        return resolvedAdm != null ? bid.toBuilder().adm(resolvedAdm).build() : bid;
    }

    private String resolveBidAdm(String adm, List<BidderError> errors) {
        final Response response;
        try {
            response = mapper.decodeValue(adm, Response.class);
        } catch (DecodeException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }

        final List<EventTracker> eventtrackers = response.getEventtrackers();

        if (CollectionUtils.isEmpty(eventtrackers)) {
            return null;
        }

        final List imptrackers = ListUtils.defaultIfNull(response.getImptrackers(), new ArrayList<>());

        String jstracker = response.getJstracker();
        for (EventTracker eventTracker : eventtrackers) {
            if (!Objects.equals(eventTracker.getEvent(), EVENT_TYPE_IMPRESSION)) {
                continue;
            }

            final Integer currentMethod = eventTracker.getMethod();
            if (Objects.equals(currentMethod, IMAGE_TRACKER_METHOD)) {
                imptrackers.add(eventTracker.getUrl());
            } else if (Objects.equals(currentMethod, JS_TRACKER_METHOD)) {
                jstracker = String.format("<script src=\"%s\"></script>", eventTracker.getUrl());
            }
        }

        return mapper.encode(response.toBuilder()
                .eventtrackers(null)
                .jstracker(jstracker)
                .imptrackers(imptrackers)
                .build());
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getXNative() != null) {
                    return BidType.xNative;
                } else if (imp.getBanner() != null) {
                    return BidType.banner;
                }
            }
        }
        throw new PreBidException(String.format("Failed to find native/banner impression \"%s\"", impId));
    }
}
