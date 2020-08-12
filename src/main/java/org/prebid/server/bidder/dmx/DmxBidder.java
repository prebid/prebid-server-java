package org.prebid.server.bidder.dmx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final int INT_VALUE = 1;
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
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> validImps = new ArrayList<>();

        if (request.getUser() == null) {
            if (request.getApp() == null) {
                errors.add(BidderError.badInput("No user id or app id found. Could not send request to DMX."));
                return Result.of(Collections.emptyList(), errors);
            }
        }

        final List<Imp> imps = request.getImp();
        if (CollectionUtils.isEmpty(imps)) {
            errors.add(BidderError.badInput("No valid impressions in the bid request"));
            return Result.of(Collections.emptyList(), errors);
        }

        String updatedPublisherId = null;
        String updatedSellerId = null;
        for (int i = 0; i < imps.size(); i++) {
            try {
                final ExtImpDmx extImp = parseImpExt(imps.get(i));
                if (i == 0) {
                    final String publisherId = extImp.getPublisherId();
                    updatedPublisherId = StringUtils.isBlank(publisherId)
                            ? publisherId
                            : extImp.getMemberId();
                    updatedSellerId = extImp.getSellerId();
                }
                final Imp validImp = validateAndModifyImp(imps.get(i), extImp);
                validImps.add(validImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        boolean anyHasId = false;
        if (request.getApp() != null) {
            if (StringUtils.isNotBlank(request.getApp().getId())) {
                anyHasId = true;
            }
        }

        final Site site = request.getSite();
        final Publisher publisher = site.getPublisher();
        if (site.getPublisher() != null) {
            request.toBuilder()
                    .site(site.toBuilder().publisher(publisher.toBuilder().id(updatedPublisherId).build()).build())
                    .build();
        } else {
            request.toBuilder()
                    .site(site.toBuilder().publisher(Publisher.builder().id(updatedPublisherId).build()).build())
                    .build();
        }

        final User user = request.getUser();
        if (user != null) {
            if (StringUtils.isNotBlank(user.getId())) {
                anyHasId = true;
            }
            final ExtUser ext = user.getExt();
            if (ext != null) {
                final ExtUserDigiTrust digitrust = ext.getDigitrust();
                if (CollectionUtils.isNotEmpty(ext.getEids()) || (digitrust != null
                        && StringUtils.isNotBlank(digitrust.getId()))) {
                    anyHasId = true;
                }
            }
        }

        if (!anyHasId) {
            return Result.emptyWithError(BidderError.badInput("This request contained no identifier"));
        }

        final BidRequest outgoingRequest = request.toBuilder().imp(validImps).build();
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
                        .payload(outgoingRequest)
                        .body(body)
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

    private Imp validateAndModifyImp(Imp imp, ExtImpDmx extImp) {
        Imp updatedImp = null;
        final Banner banner = imp.getBanner();
        if (banner != null) {
            if (CollectionUtils.isNotEmpty(banner.getFormat())) {
                updatedImp = updateImp(imp, extImp).toBuilder().banner(banner).build();
            }
        }

        final Video video = imp.getVideo();
        if (video != null) {
            updatedImp = updateImp(imp, extImp).toBuilder().video(video).build();
        }
        return updatedImp;
    }

    private Imp updateImp(Imp imp, ExtImpDmx extImp) {
        Imp updatedImp;
        if (StringUtils.isNotBlank(extImp.getPublisherId())
                || StringUtils.isNotBlank(extImp.getMemberId())) {
            updatedImp = fetchParams(imp, extImp);
        } else {
            throw new PreBidException("Missing Params for auction to be send");
        }
        return updatedImp;
    }

    private Imp fetchParams(Imp imp, ExtImpDmx extImp) {
        Imp updatedImp = null;
        final String tagId = extImp.getTagId();
        if (StringUtils.isNotBlank(tagId)) {
            updatedImp = Imp.builder()
                    .id(imp.getId())
                    .tagid(tagId)
                    .ext(imp.getExt())
                    .secure(INT_VALUE)
                    .build();
        }

        final String dmxId = extImp.getDmxId();
        if (StringUtils.isNotBlank(dmxId)) {
            updatedImp = Imp.builder()
                    .id(imp.getId())
                    .tagid(dmxId)
                    .ext(imp.getExt())
                    .secure(INT_VALUE)
                    .build();
        }

        return (updatedImp != null && StringUtils.isBlank(updatedImp.getTagid())) ? imp : updatedImp;
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
                    String adm = null;
                    if (bidType == BidType.video) {
                        adm = getAdm(bid);
                    }

                    final Bid updatedBid = bid.toBuilder().adm(adm).build();
                    final BidderBid bidderBid = BidderBid.of(updatedBid, bidType, DEFAULT_BID_CURRENCY);
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

    private BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                return imp.getVideo() != null ? BidType.video : BidType.banner;
            }
        }
        throw new PreBidException(String.format("Failed to find impression %s", impId));
    }

    private String getAdm(Bid bid) {
        final String wrappedNurl = String.format(IMP, bid.getNurl());
        return bid.getAdm().replaceFirst(SEARCH, wrappedNurl);
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
