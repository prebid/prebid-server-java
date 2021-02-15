package org.prebid.server.bidder.dmx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.dmx.model.DmxPublisherExtId;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.dmx.ExtImpDmx;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Dmx {@link Bidder} implementation.
 */
public class DmxBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpDmx>> DMX_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpDmx>>() {
            };

    private static final int SECURE = 1;
    private static final String IMP = "</Impression><Impression><![CDATA[%s]]></Impression>";
    private static final String SEARCH = "</Impression>";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public DmxBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        try {
            validateUserAndApp(request.getApp(), request.getUser());
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> imps = request.getImp();

        String updatedPublisherId = null;
        String updatedSellerId = null;
        try {
            final ExtImpDmx extImp = parseImpExt(imps.get(0));
            final String extImpPublisherId = extImp.getPublisherId();
            updatedPublisherId = StringUtils.isNotBlank(extImpPublisherId) ? extImpPublisherId : extImp.getMemberId();
            updatedSellerId = extImp.getSellerId();
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
        }

        final List<Imp> validImps = new ArrayList<>();
        for (Imp imp : imps) {
            try {
                final ExtImpDmx extImp = parseImpExt(imp);
                if (StringUtils.isAllBlank(extImp.getPublisherId(), extImp.getMemberId())) {
                    return Result.withError(BidderError.badInput("Missing Params for auction to be send"));
                }

                final Imp validImp = modifyImp(imp, extImp);
                if (validImp != null) {
                    validImps.add(validImp);
                }
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final BidRequest outgoingRequest = request.toBuilder()
                .imp(validImps)
                .site(modifySite(request.getSite(), updatedPublisherId))
                .app(modifyApp(request.getApp(), updatedPublisherId))
                .build();

        final String urlParameter = StringUtils.isNotBlank(updatedSellerId)
                ? "?sellerid=" + HttpUtil.encodeUrl(updatedSellerId)
                : "";
        final String uri = String.format("%s%s", endpointUrl, urlParameter);

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(uri)
                        .headers(HttpUtil.headers())
                        .body(mapper.encode(outgoingRequest))
                        .payload(outgoingRequest)
                        .build()),
                errors);
    }

    private ExtImpDmx parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), DMX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static Imp modifyImp(Imp imp, ExtImpDmx extImp) {
        final Imp updatedImp = fetchParams(imp, extImp);
        if (updatedImp == null) {
            return null;
        }

        if (updatedImp.getVideo() != null) {
            return updatedImp;
        }

        final Banner banner = updatedImp.getBanner();
        if (banner != null && CollectionUtils.isNotEmpty(banner.getFormat())) {
            return updatedImp;
        }

        return null;
    }

    private static Imp fetchParams(Imp imp, ExtImpDmx extImp) {
        final String impTagId = imp.getTagid();
        final String extTagId = extImp.getTagId();
        final String dmxId = extImp.getDmxId();

        final String tagId = StringUtils.defaultIfBlank(StringUtils.defaultIfBlank(dmxId, extTagId), impTagId);
        if (StringUtils.isBlank(tagId)) {
            return null;
        }

        BigDecimal bidFloor = imp.getBidfloor();
        final BigDecimal extBidFloor = extImp.getBidFloor();
        if (extBidFloor != null && extBidFloor.compareTo(BigDecimal.ZERO) > 0) {
            bidFloor = extBidFloor;
        }

        return imp.toBuilder()
                .tagid(tagId)
                .secure(StringUtils.isAllBlank(extTagId, dmxId) ? imp.getSecure() : SECURE)
                .bidfloor(bidFloor)
                .build();
    }

    private static void validateUserAndApp(App app, User user) {
        if (user == null && app == null) {
            throw new PreBidException("No user id or app id found. Could not send request to DMX.");
        }

        if (app != null && StringUtils.isNotBlank(app.getId())) {
            return;
        }

        if (user != null) {
            if (StringUtils.isNotBlank(user.getId())) {
                return;
            }

            final ExtUser userExt = user.getExt();
            // Notice that digitrust is absent to keep prebid convention
            if (userExt != null && CollectionUtils.isNotEmpty(userExt.getEids())) {
                return;
            }
        }

        throw new PreBidException("This request contained no identifier");
    }

    private Site modifySite(Site site, String updatedPublisherId) {
        return site == null
                ? null
                : site.toBuilder()
                        .publisher(modifyPublisher(site.getPublisher(), updatedPublisherId, false))
                        .build();
    }

    private App modifyApp(App app, String updatedPublisherId) {
        return app == null
                ? null
                : app.toBuilder()
                        .publisher(modifyPublisher(app.getPublisher(), updatedPublisherId, true))
                        .build();
    }

    private Publisher modifyPublisher(Publisher publisher, String updatedPublisherId, boolean extOnEmptyPublisher) {

        final DmxPublisherExtId dmxPublisherExtId = DmxPublisherExtId.of(updatedPublisherId);
        final ObjectNode encodedPublisherExt = mapper.mapper().valueToTree(dmxPublisherExtId);
        final ExtPublisher extPublisher = ExtPublisher.empty();
        extPublisher.addProperty("dmx", encodedPublisherExt);

        if (publisher == null) {
            return Publisher.builder()
                    .id(updatedPublisherId)
                    .ext(extOnEmptyPublisher ? extPublisher : null)
                    .build();
        } else {
            return publisher.toBuilder()
                    .id(ObjectUtils.firstNonNull(publisher.getId(), updatedPublisherId))
                    .ext(extPublisher)
                    .build();
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;
        try {
            bidResponse = decodeBodyToBidResponse(httpCall);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<BidderBid> bidderBids = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();
        for (SeatBid seatBid : bidResponse.getSeatbid()) {
            for (Bid bid : seatBid.getBid()) {
                try {
                    final BidType bidType = getBidType(bid.getImpid(), bidRequest.getImp());
                    final Bid updatedBid = bidType == BidType.video
                            ? bid.toBuilder().adm(getAdm(bid)).build()
                            : bid;
                    final BidderBid bidderBid = BidderBid.of(updatedBid, bidType, bidResponse.getCur());
                    bidderBids.add(bidderBid);
                } catch (PreBidException e) {
                    errors.add(BidderError.badInput(e.getMessage()));
                }
            }
        }
        return Result.of(bidderBids, errors);
    }

    private BidResponse decodeBodyToBidResponse(HttpCall<BidRequest> httpCall) {
        try {
            return mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        return imps.stream()
                .filter(imp -> Objects.equals(imp.getId(), impId))
                .map(imp -> imp.getVideo() != null ? BidType.video : BidType.banner)
                .findFirst()
                .orElseThrow(() -> new PreBidException(String.format("Failed to find impression %s", impId)));
    }

    private static String getAdm(Bid bid) {
        final String wrappedNurl = String.format(IMP, bid.getNurl());
        return bid.getAdm().replaceFirst(SEARCH, wrappedNurl);
    }
}
