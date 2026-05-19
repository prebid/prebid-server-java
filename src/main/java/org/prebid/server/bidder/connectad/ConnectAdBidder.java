package org.prebid.server.bidder.connectad;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.MultiMap;
import org.apache.commons.collections4.CollectionUtils;
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
import org.prebid.server.proto.openrtb.ext.request.connectad.ExtImpConnectAd;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ConnectAdBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpConnectAd>> CONNECTAD_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String HTTPS_PREFIX = "https";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ConnectAdBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final int secure = secureFrom(request.getSite());

        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> processedImps = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                if (imp.getBanner() == null && imp.getVideo() == null && imp.getXNative() == null && imp.getAudio() == null) {
                    throw new PreBidException("We need a Banner, Video, Native or Audio Object in the request");
                }
                final ExtImpConnectAd impExt = parseImpExt(imp);
                final Imp updatedImp = updateImp(imp, secure, impExt);
                processedImps.add(updatedImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        if (CollectionUtils.isNotEmpty(errors)) {
            errors.add(BidderError.badInput("Error in preprocess of Imp"));
            return Result.withErrors(errors);
        }
        final BidRequest outgoingRequest = request.toBuilder().imp(processedImps).build();

        return Result.of(
                Collections.singletonList(BidderUtil.defaultRequest(
                        outgoingRequest,
                        resolveHeaders(outgoingRequest.getDevice()),
                        endpointUrl,
                        mapper)),
                errors);
    }

    private static int secureFrom(Site site) {
        final String page = site != null ? site.getPage() : null;
        return page != null && page.startsWith(HTTPS_PREFIX) ? 1 : 0;
    }

    private ExtImpConnectAd parseImpExt(Imp imp) {
        final ExtImpConnectAd extImpConnectAd;
        try {
            extImpConnectAd = mapper.mapper().convertValue(imp.getExt(), CONNECTAD_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Impression id=%s, has invalid Ext".formatted(imp.getId()));
        }
        final String siteId = extImpConnectAd.getSiteId();
        if (siteId == null) {
            throw new PreBidException("Impression id=%s, has no siteId present".formatted(imp.getId()));
        }
        return extImpConnectAd;
    }

    private Imp updateImp(Imp imp, Integer secure, ExtImpConnectAd extImpConnectAd) {
        final BigDecimal bidFloor = extImpConnectAd.getBidFloor();
        final boolean isValidBidFloor = BidderUtil.isValidPrice(bidFloor);
        return imp.toBuilder()
                .banner(updateBanner(imp.getBanner()))
                .tagid(extImpConnectAd.getSiteId())
                .secure(secure)
                .bidfloor(isValidBidFloor ? bidFloor : imp.getBidfloor())
                .bidfloorcur(isValidBidFloor ? "USD" : imp.getBidfloorcur())
                .ext(modifyImpExt(imp.getExt(), extImpConnectAd))
                .build();
    }

    private ObjectNode modifyImpExt(ObjectNode impExt, ExtImpConnectAd extImpConnectAd) {
        final ObjectNode modifiedExt = impExt != null ? impExt.deepCopy() : mapper.mapper().createObjectNode();
        final String networkId = extImpConnectAd.getNetworkId();
        final String siteId = extImpConnectAd.getSiteId();

        if (networkId != null) {
            try {
                modifiedExt.put("networkId", Integer.parseInt(networkId));
            } catch (NumberFormatException e) {
                modifiedExt.put("networkId", networkId);
            }
        }

        if (siteId != null) {
            try {
                modifiedExt.put("siteId", Integer.parseInt(siteId));
            } catch (NumberFormatException e) {
                modifiedExt.put("siteId", siteId);
            }
        }

        return modifiedExt;
    }

    private static Banner updateBanner(Banner banner) {
        if (banner == null) {
            return null;
        }

        if (banner.getW() != null || banner.getH() != null) {
            return banner;
        }

        final List<Format> formats = banner.getFormat();
        if (CollectionUtils.isEmpty(formats)) {
            throw new PreBidException("At least one size is required");
        }

        final Format firstFormat = formats.getFirst();
        final List<Format> slicedFormats = new ArrayList<>(formats);
        slicedFormats.removeFirst();

        return banner.toBuilder()
                .format(slicedFormats)
                .w(firstFormat.getW())
                .h(firstFormat.getH())
                .build();
    }

    private static MultiMap resolveHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers();

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ACCEPT_LANGUAGE_HEADER, device.getLanguage());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIpv6());

            final Integer dnt = device.getDnt();
            headers.add(HttpUtil.DNT_HEADER, dnt != null ? dnt.toString() : "0");
        }
        return headers;
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse, bidRequest));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid, bidRequest), bidResponse.getCur()))
                .toList();
    }

    private static BidType getBidType(Bid bid, BidRequest bidRequest) {
        final Integer mType = bid.getMtype();
        if (mType != null) {
            return switch (mType) {
                case 1 -> BidType.banner;
                case 2 -> BidType.video;
                case 3 -> BidType.audio;
                case 4 -> BidType.xNative;
                default -> BidType.banner;
            };
        }

        if (bidRequest != null && CollectionUtils.isNotEmpty(bidRequest.getImp())) {
            for (Imp imp : bidRequest.getImp()) {
                if (imp.getId().equals(bid.getImpid())) {
                    if (imp.getBanner() != null) {
                        return BidType.banner;
                    } else if (imp.getVideo() != null) {
                        return BidType.video;
                    } else if (imp.getXNative() != null) {
                        return BidType.xNative;
                    } else if (imp.getAudio() != null) {
                        return BidType.audio;
                    }
                }
            }
        }

        return BidType.banner;
    }
}
