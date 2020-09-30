package org.prebid.server.bidder.gamma;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.gamma.model.GammaBid;
import org.prebid.server.bidder.gamma.model.GammaBidResponse;
import org.prebid.server.bidder.gamma.model.GammaSeatBid;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.gamma.ExtImpGamma;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GammaBidder implements Bidder<Void> {

    private static final TypeReference<ExtPrebid<?, ExtImpGamma>> GAMMA_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpGamma>>() {
            };

    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public GammaBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<Void>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<Void>> outgoingRequests;
        try {
            outgoingRequests = createHttpRequests(bidRequest, errors);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return Result.of(Collections.emptyList(), errors);
        }
        return Result.of(outgoingRequests, errors);
    }

    private List<HttpRequest<Void>> createHttpRequests(BidRequest bidRequest, List<BidderError> errors) {
        final List<Imp> modifiedImps = new ArrayList<>();
        for (Imp imp : bidRequest.getImp()) {
            try {
                modifiedImps.add(modifyImp(imp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (modifiedImps.isEmpty()) {
            throw new PreBidException("No valid impressions");
        }

        final List<HttpRequest<Void>> httpRequests = new ArrayList<>();
        for (Imp modifiedImp : modifiedImps) {
            try {
                httpRequests.add(makeHttpRequest(bidRequest, modifiedImp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return httpRequests;
    }

    private static Imp modifyImp(Imp imp) {
        if (imp.getVideo() == null && imp.getBanner() == null) {
            throw new PreBidException(String.format("Gamma only supports banner and video media types. "
                    + "Ignoring imp id= %s", imp.getId()));
        }

        final Banner banner = imp.getBanner();
        if (banner != null) {
            final List<Format> format = banner.getFormat();
            if (banner.getW() == null && banner.getH() == null && CollectionUtils.isNotEmpty(format)) {
                final Format firstFormat = format.get(0);
                final Banner modifiedBanner = banner.toBuilder().w(firstFormat.getW()).h(firstFormat.getH()).build();
                return imp.toBuilder().banner(modifiedBanner).build();
            }
        }
        return imp;
    }

    private HttpRequest<Void> makeHttpRequest(BidRequest bidRequest, Imp imp) {
        final ExtImpGamma extImpGamma = parseImpExt(imp);

        if (StringUtils.isBlank(extImpGamma.getId())) {
            throw new PreBidException("PartnerID is empty");
        }
        if (StringUtils.isBlank(extImpGamma.getZid())) {
            throw new PreBidException("ZoneID is empty");
        }
        if (StringUtils.isBlank(extImpGamma.getWid())) {
            throw new PreBidException("WebID is empty");
        }

        final Device device = bidRequest.getDevice();

        return HttpRequest.<Void>builder()
                .method(HttpMethod.GET)
                .uri(makeUri(extImpGamma, imp.getId(), device, bidRequest.getApp()))
                .headers(makeHeaders(device))
                .build();
    }

    private ExtImpGamma parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), GAMMA_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("ext.bidder.publisher not provided");
        }
    }

    private String makeUri(ExtImpGamma extImpGamma, String impId, Device device, App app) {
        final StringBuilder uri = new StringBuilder(endpointUrl)
                .append("?id=").append(extImpGamma.getId())
                .append("&zid=").append(extImpGamma.getZid())
                .append("&wid=").append(extImpGamma.getWid())
                .append("&bidid=").append(ObjectUtils.defaultIfNull(impId, ""))
                .append("&hb=pbmobile");

        if (device != null) {
            if (StringUtils.isNotBlank(device.getIp())) {
                uri.append("&device_ip=").append(device.getIp());
            }
            if (StringUtils.isNotBlank(device.getModel())) {
                uri.append("&device_model=").append(device.getModel());
            }
            if (StringUtils.isNotBlank(device.getOs())) {
                uri.append("&device_os=").append(device.getOs());
            }
            if (StringUtils.isNotBlank(device.getUa())) {
                uri.append("&device_ua=").append(HttpUtil.encodeUrl(device.getUa()));
            }
            if (StringUtils.isNotBlank(device.getIfa())) {
                uri.append("&device_ifa=").append(device.getIfa());
            }
        }

        if (app != null) {
            if (StringUtils.isNotBlank(app.getId())) {
                uri.append("&app_id=").append(app.getId());
            }
            if (StringUtils.isNotBlank(app.getBundle())) {
                uri.append("&app_bundle=").append(app.getBundle());
            }
            if (StringUtils.isNotBlank(app.getName())) {
                uri.append("&app_name=").append(app.getName());
            }
        }
        return uri.toString();
    }

    private MultiMap makeHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers().clear()
                .set(HttpUtil.ACCEPT_HEADER, "*/*")
                .set(HttpUtil.CACHE_CONTROL_HEADER, "no-cache")
                .set("x-openrtb-version", "2.5")
                .set("Connection", "keep-alive")
                .set("Accept-Encoding", "gzip, deflate");

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ACCEPT_LANGUAGE_HEADER, device.getLanguage());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.DNT_HEADER, Objects.toString(device.getDnt(), null));
        }

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<Void> httpCall, BidRequest bidRequest) {
        if (httpCall.getResponse().getStatusCode() == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }

        final String body = httpCall.getResponse().getBody();
        if (body == null) {
            return Result.emptyWithError(BidderError.badServerResponse("bad server response: body is empty"));
        }

        try {
            final GammaBidResponse bidResponse = mapper.decodeValue(body, GammaBidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            return Result.of(extractBidsAndFillErorrs(bidResponse, bidRequest, errors), errors);
        } catch (DecodeException e) {
            return Result.emptyWithError(BidderError.badServerResponse(
                    String.format("bad server response: %s", e.getMessage())));
        }
    }

    private static List<BidderBid> extractBidsAndFillErorrs(GammaBidResponse bidResponse,
                                                            BidRequest bidRequest,
                                                            List<BidderError> errors) {
        return bidResponse == null || bidResponse.getSeatbid() == null
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse, bidRequest, errors);
    }

    private static List<BidderBid> bidsFromResponse(GammaBidResponse bidResponse,
                                                    BidRequest bidRequest,
                                                    List<BidderError> errors) {
        final List<BidderBid> bidderBids = new ArrayList<>();
        for (GammaSeatBid gammaSeatBid : bidResponse.getSeatbid()) {
            for (GammaBid gammaBid : gammaSeatBid.getBid()) {
                try {
                    final BidType mediaType = getMediaTypes(bidResponse.getId(), bidRequest.getImp());
                    final Bid updatedBid = convertBid(gammaBid, mediaType);
                    bidderBids.add(BidderBid.of(updatedBid, mediaType, DEFAULT_BID_CURRENCY));
                } catch (PreBidException e) {
                    errors.add(BidderError.badServerResponse(e.getMessage()));
                }
            }
        }
        return bidderBids;
    }

    private static BidType getMediaTypes(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId) && imp.getVideo() != null) {
                return BidType.video;
            }
        }
        return BidType.banner;
    }

    private static Bid convertBid(GammaBid gammaBid, BidType bidType) {
        final boolean isVideo = BidType.video.equals(bidType);
        if (!isVideo && StringUtils.isBlank(gammaBid.getAdm())) {
            throw new PreBidException("Missing Ad Markup. Run with request.debug = 1 for more info");
        }

        if (isVideo) {
            //Return inline VAST XML Document (Section 6.4.2)
            final String vastXml = gammaBid.getVastXml();
            if (StringUtils.isNotBlank(vastXml)) {
                final Bid.BidBuilder<?, ?> bidBuilder = gammaBid.toBuilder().adm(vastXml);

                final String vastUrl = gammaBid.getVastUrl();
                if (StringUtils.isNotBlank(vastUrl)) {
                    bidBuilder.nurl(vastUrl);
                }

                return bidBuilder.build();
            } else {
                throw new PreBidException("Missing Ad Markup. Run with request.debug = 1 for more info");
            }
        }

        return gammaBid;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}

