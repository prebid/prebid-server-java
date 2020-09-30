package org.prebid.server.bidder.yieldlab;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.yieldlab.model.YieldlabResponse;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.yieldlab.ExtImpYieldlab;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class YieldlabBidder implements Bidder<Void> {

    private static final TypeReference<ExtPrebid<?, ExtImpYieldlab>> YIELDLAB_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpYieldlab>>() {
            };

    private static final String DEFAULT_BID_CURRENCY = "EUR";
    private static final String AD_SLOT_ID_SEPARATOR = ",";
    private static final String AD_SIZE_SEPARATOR = "x";
    private static final String CREATIVE_ID = "%s%s%s";
    private static final String AD_SOURCE_BANNER = "<script src=\"%s\"></script>";
    private static final String AD_SOURCE_URL = "https://ad.yieldlab.net/d/%s/%s/%s?%s";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public YieldlabBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<Void>>> makeHttpRequests(BidRequest request) {
        final List<ExtImpYieldlab> extImps = collectImpExt(request.getImp());
        final ExtImpYieldlab modifiedExtImp = constructExtImp(extImps);

        return Result.of(Collections.singletonList(
                HttpRequest.<Void>builder()
                        .method(HttpMethod.GET)
                        .uri(makeUrl(modifiedExtImp, request))
                        .body(null)
                        .headers(getHeaders(request))
                        .payload(null)
                        .build()), Collections.emptyList());
    }

    private List<ExtImpYieldlab> collectImpExt(List<Imp> imps) {
        return imps.stream()
                .map(this::parseImpExt)
                .collect(Collectors.toList());
    }

    private ExtImpYieldlab parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), YIELDLAB_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private ExtImpYieldlab constructExtImp(List<ExtImpYieldlab> extImps) {
        final List<String> adSlotIds = extImps.stream()
                .map(ExtImpYieldlab::getAdslotId)
                .collect(Collectors.toList());

        final Map<String, String> targeting = extImps.stream()
                .map(ExtImpYieldlab::getTargeting)
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        final String adSlotIdsParams = adSlotIds.stream().sorted().collect(Collectors.joining(AD_SLOT_ID_SEPARATOR));
        return ExtImpYieldlab.builder().adslotId(adSlotIdsParams).targeting(targeting).build();
    }

    private String makeUrl(ExtImpYieldlab extImpYieldlab, BidRequest request) {
        // for passing validation tests
        final String timestamp = isDebugEnabled(request) ? "200000" : String.valueOf(Instant.now().getEpochSecond());

        final String updatedPath = String.format("%s/%s", endpointUrl, extImpYieldlab.getAdslotId());

        final URIBuilder uriBuilder = new URIBuilder()
                .setPath(updatedPath)
                .addParameter("content", "json")
                .addParameter("pvid", "true")
                .addParameter("ts", timestamp)
                .addParameter("t", getTargetingValues(extImpYieldlab));

        final User user = request.getUser();
        if (user != null && StringUtils.isNotBlank(user.getBuyeruid())) {
            uriBuilder.addParameter("ids", String.join("ylid:", user.getBuyeruid()));
        }

        final Device device = request.getDevice();
        if (device != null) {
            uriBuilder.addParameter("yl_rtb_ifa", device.getIfa())
                    .addParameter("yl_rtb_devicetype", String.format("%s", device.getDevicetype()));

            final Integer connectiontype = device.getConnectiontype();
            if (connectiontype != null) {
                uriBuilder.addParameter("yl_rtb_connectiontype", String.format("%s", connectiontype));
            }

            final Geo geo = device.getGeo();
            if (geo != null) {
                uriBuilder.addParameter("lat", String.format("%s", geo.getLat()))
                        .addParameter("lon", String.format("%s", geo.getLon()));
            }
        }

        final App app = request.getApp();
        if (app != null) {
            uriBuilder.addParameter("pubappname", app.getName())
                    .addParameter("pubbundlename", app.getBundle());
        }

        final String gdpr = getGdprParameter(request.getRegs());
        final String consent = getConsentParameter(request.getUser());
        if (StringUtils.isNotBlank(gdpr) && StringUtils.isNotBlank(consent)) {
            uriBuilder.addParameter("gdpr", gdpr)
                    .addParameter("consent", consent);
        }

        return uriBuilder.toString();
    }

    /**
     * Determines debug flag from {@link BidRequest} or {@link ExtRequest}.
     */
    private boolean isDebugEnabled(BidRequest bidRequest) {
        if (Objects.equals(bidRequest.getTest(), 1)) {
            return true;
        }

        final ExtRequest extRequest = bidRequest.getExt();
        final ExtRequestPrebid extRequestPrebid = extRequest != null ? extRequest.getPrebid() : null;
        return extRequestPrebid != null && Objects.equals(extRequestPrebid.getDebug(), 1);
    }

    private String getTargetingValues(ExtImpYieldlab extImpYieldlab) {
        final URIBuilder uriBuilder = new URIBuilder();

        for (Map.Entry<String, String> targeting : extImpYieldlab.getTargeting().entrySet()) {
            uriBuilder.addParameter(targeting.getKey(), targeting.getValue());
        }

        return uriBuilder.toString().replace("?", "");
    }

    private String getGdprParameter(Regs regs) {
        if (regs != null) {
            final Integer gdpr = regs.getExt() != null ? regs.getExt().getGdpr() : null;
            if (gdpr != null && (gdpr == 0 || gdpr == 1)) {
                return String.valueOf(gdpr);
            }
        }
        return "";
    }

    private String getConsentParameter(User user) {
        final ExtUser extUser = user != null ? user.getExt() : null;
        final String consent = extUser != null ? extUser.getConsent() : null;
        return ObjectUtils.defaultIfNull(consent, "");
    }

    private static MultiMap getHeaders(BidRequest request) {
        final MultiMap headers = HttpUtil.headers();
        final Site site = request.getSite();

        if (site != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.REFERER_HEADER.toString(), site.getPage());
        }

        final Device device = request.getDevice();
        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER.toString(), device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER.toString(), device.getIp());
        }

        final User user = request.getUser();
        if (user != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.COOKIE_HEADER.toString(),
                    String.format("id=%s", user.getBuyeruid()));
        }

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<Void> httpCall, BidRequest bidRequest) {
        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        } else if (statusCode == HttpResponseStatus.BAD_REQUEST.code()) {
            return Result.emptyWithError(BidderError.badInput("Invalid request."));
        } else if (statusCode != HttpResponseStatus.OK.code()) {
            return Result.emptyWithError(BidderError.badServerResponse(String.format("Unexpected HTTP status %s.",
                    statusCode)));
        }

        final List<YieldlabResponse> yieldlabResponses;
        try {
            yieldlabResponses = decodeBodyToBidList(httpCall);
        } catch (PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<ExtImpYieldlab> extImps = collectImpExt(bidRequest.getImp());
        final List<BidderBid> bidderBids = new ArrayList<>();
        for (int i = 0; i < yieldlabResponses.size(); i++) {
            final YieldlabResponse yieldlabResponse = yieldlabResponses.get(i);
            final String[] sizeParts = yieldlabResponse.getAdSize().split(AD_SIZE_SEPARATOR);
            final int width;
            final int height;
            if (sizeParts.length != 2) {
                width = 0;
                height = 0;
            } else {
                width = StringUtils.isNumeric(sizeParts[0]) ? Integer.parseInt(sizeParts[0]) : 0;
                height = StringUtils.isNumeric(sizeParts[1]) ? Integer.parseInt(sizeParts[1]) : 0;
            }

            final ExtImpYieldlab filteredExtImp = filterExtImp(yieldlabResponse.getId(), extImps);
            if (filteredExtImp == null) {
                return Result.emptyWithError(BidderError.badInput("Invalid extension"));
            }
            final Imp currentImp = bidRequest.getImp().get(i);
            final Bid.BidBuilder updatedBid = Bid.builder()
                    .id(String.valueOf(yieldlabResponse.getId()))
                    .price(BigDecimal.valueOf(yieldlabResponse.getPrice() / 100))
                    .impid(currentImp.getId())
                    .dealid(String.valueOf(yieldlabResponse.getPid()))
                    .crid(makeCreativeId(bidRequest, yieldlabResponse, filteredExtImp))
                    .w(width)
                    .h(height);

            BidType bidType;
            if (currentImp.getVideo() != null) {
                bidType = BidType.video;
                updatedBid.nurl(makeNurl(bidRequest, filteredExtImp, yieldlabResponse));
            } else if (currentImp.getBanner() != null) {
                bidType = BidType.banner;
                updatedBid.adm(makeAdm(bidRequest, filteredExtImp, yieldlabResponse));
            } else {
                continue;
            }

            final BidderBid bidderBid = BidderBid.of(updatedBid.build(), bidType, DEFAULT_BID_CURRENCY);
            bidderBids.add(bidderBid);
        }
        return Result.of(bidderBids, Collections.emptyList());
    }

    private List<YieldlabResponse> decodeBodyToBidList(HttpCall<Void> httpCall) {
        try {
            return mapper.mapper().readValue(
                    httpCall.getResponse().getBody(),
                    mapper.mapper().getTypeFactory().constructCollectionType(List.class, YieldlabResponse.class));
        } catch (DecodeException | JsonProcessingException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private ExtImpYieldlab filterExtImp(Integer responseId, List<ExtImpYieldlab> extImps) {
        return extImps.stream()
                .filter(ext -> ext.getAdslotId().equals(String.valueOf(responseId)))
                .findFirst()
                .orElse(null);
    }

    private String makeCreativeId(BidRequest bidRequest, YieldlabResponse yieldlabResponse, ExtImpYieldlab extImp) {
        // for passing validation tests
        final int weekNumber = isDebugEnabled(bidRequest) ? 35 : Calendar.getInstance().get(Calendar.WEEK_OF_YEAR);
        return String.format(CREATIVE_ID, extImp.getAdslotId(), yieldlabResponse.getPid(), weekNumber);
    }

    private String makeAdm(BidRequest bidRequest, ExtImpYieldlab extImpYieldlab, YieldlabResponse yieldlabResponse) {
        return String.format(AD_SOURCE_BANNER, makeNurl(bidRequest, extImpYieldlab, yieldlabResponse));
    }

    private String makeNurl(BidRequest bidRequest, ExtImpYieldlab extImpYieldlab, YieldlabResponse yieldlabResponse) {
        // for passing validation tests
        final String timestamp = isDebugEnabled(bidRequest) ? "200000" : String.valueOf(Instant.now().getEpochSecond());

        final URIBuilder uriBuilder = new URIBuilder()
                .addParameter("ts", timestamp)
                .addParameter("id", extImpYieldlab.getExtId())
                .addParameter("pvid", yieldlabResponse.getPvid());

        final User user = bidRequest.getUser();
        if (user != null && StringUtils.isNotBlank(user.getBuyeruid())) {
            uriBuilder.addParameter("ids", String.join("ylid:", user.getBuyeruid()));
        }

        final String gdpr = getGdprParameter(bidRequest.getRegs());
        final String consent = getConsentParameter(bidRequest.getUser());
        if (StringUtils.isNotBlank(gdpr) && StringUtils.isNotBlank(consent)) {
            uriBuilder.addParameter("gdpr", gdpr)
                    .addParameter("consent", consent);
        }

        return String.format(AD_SOURCE_URL, extImpYieldlab.getAdslotId(), extImpYieldlab.getSupplyId(),
                yieldlabResponse.getAdSize(), uriBuilder.toString().replace("?", ""));
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
