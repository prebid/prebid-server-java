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
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
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
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;
import org.prebid.server.proto.openrtb.ext.request.dmx.ExtImpDmx;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * DmxBidder {@link Bidder} implementation.
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
        if (request.getUser() == null && request.getApp() == null) {
            return Result.emptyWithError(
                    BidderError.badInput("No user id or app id found. Could not send request to DMX."));
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> imps = request.getImp();

        String updatedPublisherId = null;
        String updatedSellerId = null;
        try {
            final ExtImpDmx extImp = parseImpExt(imps.get(0));
            final String publisherId = extImp.getPublisherId();
            updatedPublisherId = StringUtils.isNotBlank(publisherId) ? publisherId : extImp.getMemberId();
            updatedSellerId = extImp.getSellerId();
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
        }

        final List<Imp> validImps = new ArrayList<>();
        for (Imp imp : imps) {
            try {
                final Imp validImp = validateAndModifyImp(imp, parseImpExt(imp));
                if (validImp != null) {
                    validImps.add(validImp);
                }
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final Site modifiedSite = modifySite(request.getSite(), updatedPublisherId);
        try {
            checkIfHasId(request.getApp(), request.getUser());
        } catch (PreBidException e) {
            return Result.emptyWithError(BidderError.badInput("This request contained no identifier"));
        }

        final BidRequest outgoingRequest = request.toBuilder().imp(validImps).site(modifiedSite).build();
        final String body = mapper.encode(outgoingRequest);
        final String urlParameter = StringUtils.isNotBlank(updatedSellerId)
                ? "?sellerid=" + HttpUtil.encodeUrl(updatedSellerId)
                : "";
        final String uri = String.format("%s%s", endpointUrl, urlParameter);

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(uri)
                        .headers(HttpUtil.headers())
                        .body(body)
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

    private static Imp validateAndModifyImp(Imp imp, ExtImpDmx extImp) {
        Imp modifiedImp = null;
        final Imp updatedImp = updateImp(imp, extImp);
        if (updatedImp != null) {
            final Banner banner = imp.getBanner();

            if (banner != null) {
                if (CollectionUtils.isNotEmpty(banner.getFormat())) {
                    modifiedImp = updatedImp.toBuilder().banner(banner).build();
                }
            }

            final Video video = imp.getVideo();
            if (video != null) {
                modifiedImp = updatedImp.toBuilder().video(video).build();
            }
        }
        return modifiedImp;
    }

    private static Imp updateImp(Imp imp, ExtImpDmx extImp) {
        if (StringUtils.isNotBlank(extImp.getPublisherId()) || StringUtils.isNotBlank(extImp.getMemberId())) {
            return fetchParams(imp, extImp);
        } else {
            throw new PreBidException("Missing Params for auction to be send");
        }
    }

    private static Imp fetchParams(Imp imp, ExtImpDmx extImp) {
        Imp updatedImp = null;

        final String tagId = extImp.getTagId();
        if (StringUtils.isNotBlank(tagId)) {
            updatedImp = Imp.builder()
                    .id(imp.getId())
                    .tagid(tagId)
                    .ext(imp.getExt())
                    .secure(SECURE)
                    .build();
        }

        final String dmxId = extImp.getDmxId();
        if (StringUtils.isNotBlank(dmxId)) {
            updatedImp = Imp.builder()
                    .id(imp.getId())
                    .tagid(dmxId)
                    .ext(imp.getExt())
                    .secure(SECURE)
                    .build();
        }

        return updatedImp;
    }

    private static Site modifySite(Site site, String updatedPublisherId) {
        Publisher updatedPublisher = null;
        if (site != null) {
            final Publisher publisher = site.getPublisher();
            updatedPublisher = publisher == null
                    ? Publisher.builder().id(updatedPublisherId).build()
                    : publisher.toBuilder().id(updatedPublisherId).build();
        }
        return site != null ? site.toBuilder().publisher(updatedPublisher).build() : null;
    }

    private static void checkIfHasId(App app, User user) {
        boolean anyHasId = false;
        if (app != null) {
            if (StringUtils.isNotBlank(app.getId())) {
                anyHasId = true;
            }
        }

        if (user != null) {
            if (StringUtils.isNotBlank(user.getId())) {
                anyHasId = true;
            }
            final ExtUser userExt = user.getExt();
            if (userExt != null) {
                final ExtUserDigiTrust digitrust = userExt.getDigitrust();
                if (CollectionUtils.isNotEmpty(userExt.getEids()) || (digitrust != null
                        && StringUtils.isNotBlank(digitrust.getId()))) {
                    anyHasId = true;
                }
            }
        }

        if (!anyHasId) {
            throw new PreBidException("This request contained no identifier");
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        } else if (statusCode == HttpResponseStatus.BAD_REQUEST.code()) {
            return Result.emptyWithError(BidderError.badInput("Invalid request."));
        } else if (statusCode != HttpResponseStatus.OK.code()) {
            return Result.emptyWithError(BidderError.badServerResponse(String.format("Unexpected HTTP status %s.",
                    statusCode)));
        }

        final BidResponse bidResponse;
        try {
            bidResponse = decodeBodyToBidResponse(httpCall);
        } catch (PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
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

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
