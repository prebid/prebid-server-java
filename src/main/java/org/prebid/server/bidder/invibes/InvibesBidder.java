package org.prebid.server.bidder.invibes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.invibes.model.InvibesBidParams;
import org.prebid.server.bidder.invibes.model.InvibesBidRequest;
import org.prebid.server.bidder.invibes.model.InvibesBidderResponse;
import org.prebid.server.bidder.invibes.model.InvibesInternalParams;
import org.prebid.server.bidder.invibes.model.InvibesPlacementProperty;
import org.prebid.server.bidder.invibes.model.InvibesTypedBid;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.invibes.ExtImpInvibes;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class InvibesBidder implements Bidder<InvibesBidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpInvibes>> INVIBES_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpInvibes>>() {
            };
    private static final String INVIBES_BID_VERSION = "4";
    private static final String ADAPTER_VERSION = "prebid_1.0.0";
    private static final String URL_HOST_MACRO = "{{Host}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public InvibesBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<InvibesBidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();

        final String consentString = resolveConsentString(request.getUser());
        final Boolean gdprApplies = resolveGDPRApplies(request.getRegs());

        InvibesInternalParams invibesInternalParams =
                InvibesInternalParams.builder()
                        .bidParams(InvibesBidParams.builder()
                                .properties(new HashMap<String, InvibesPlacementProperty>())
                                .bidVersion(INVIBES_BID_VERSION)
                                .build())
                        .build();

        for (final Imp imp : request.getImp()) {
            final ExtImpInvibes extImpInvibes;
            try {
                extImpInvibes = mapper.mapper().convertValue(imp.getExt(), INVIBES_EXT_TYPE_REFERENCE).getBidder();
            } catch (IllegalArgumentException e) {
                errors.add(BidderError.badInput("Error parsing invibesExt parameters"));
                continue;
            }
            final Banner banner = imp.getBanner();
            if (banner == null) {
                errors.add(BidderError.badInput("Banner not specified"));
                continue;
            }

            invibesInternalParams = updateInvibesInternalParams(invibesInternalParams, extImpInvibes, imp);
        }
        //TODO add AMP parameter to invibesInternalParams, after reqInfo will be implemented

        if (invibesInternalParams.getBidParams()
                .getPlacementIDs() == null
                || invibesInternalParams.getBidParams().getPlacementIDs().size() == 0) {
            return Result.of(Collections.emptyList(), errors);
        }

        invibesInternalParams = updateWithGDPRParams(invibesInternalParams, consentString, gdprApplies);

        try {
            final HttpRequest<InvibesBidRequest> httpRequest = makeRequest(invibesInternalParams, request);
            return Result.of(Collections.singletonList(httpRequest), errors);
        } catch (PreBidException e) {
            return Result.emptyWithError(BidderError.badInput(e.getMessage()));
        }
    }

    private String resolveConsentString(User user) {
        final ExtUser extUser = user != null ? user.getExt() : null;
        if (extUser != null) {
            return extUser.getConsent();
        }

        return StringUtils.EMPTY;
    }

    private Boolean resolveGDPRApplies(Regs regs) {
        final ExtRegs extRegs = regs != null ? regs.getExt() : null;
        final Integer gdpr = extRegs != null ? extRegs.getGdpr() : null;

        return gdpr != null ? gdpr == 1 : Boolean.TRUE;
    }

    private InvibesInternalParams updateInvibesInternalParams(
            InvibesInternalParams invibesInternalParams, ExtImpInvibes invibesExt,
            Imp imp) {
        final String placementId = invibesExt.getPlacementId();
        final List<String> updatedPlacementIds = getUpdatedPlacementIds(
                placementId, invibesInternalParams.getBidParams().getPlacementIDs());

        final Map<String, InvibesPlacementProperty> updatedProperties
                = getUpdatedProperties(invibesInternalParams.getBidParams().getProperties(),
                placementId, imp.getId(), resolveAdFormats(imp.getBanner()));

        final InvibesBidParams updatedBidParams = invibesInternalParams.getBidParams().toBuilder()
                .placementIDs(updatedPlacementIds)
                .properties(updatedProperties)
                .build();

        final InvibesInternalParams.InvibesInternalParamsBuilder internalParamsBuilder
                = invibesInternalParams.toBuilder()
                .domainID(invibesExt.getDomainId())
                .bidParams(updatedBidParams);

        if (invibesExt.getDebug() != null) {
            if (!invibesExt.getDebug().getTestBvid().equals(StringUtils.EMPTY)) {
                internalParamsBuilder.testBvid(invibesExt.getDebug().getTestBvid());
            }
            internalParamsBuilder.testLog(invibesExt.getDebug().getTestLog());
        }

        return internalParamsBuilder.build();
    }

    private HttpRequest<InvibesBidRequest> makeRequest(InvibesInternalParams invibesParams, BidRequest request) {
        final String url = makeUrl(invibesParams.getDomainID());
        final InvibesBidRequest parameter = resolveParameter(invibesParams, request);

        final MultiMap headers = resolveHeaders(request.getDevice(), request.getSite());

        final String body = mapper.encode(parameter);

        return HttpRequest.<InvibesBidRequest>builder()
                .method(HttpMethod.POST)
                .uri(url)
                .headers(headers)
                .payload(parameter)
                .body(body)
                .build();

    }

    private MultiMap resolveHeaders(Device device, Site site) {
        final MultiMap headers = HttpUtil.headers();
        if (device != null) {
            if (StringUtils.isNotEmpty(device.getIp())) {
                headers.add("X-Forwarded-For", device.getIp());
            } else if (StringUtils.isNotEmpty(device.getIpv6())) {
                headers.add("X-Forwarded-For", device.getIpv6());
            }
        }
        if (site != null) {
            headers.add("Referer", site.getPage());
        }
        headers.add("Aver", ADAPTER_VERSION);
        return headers;
    }

    private InvibesBidRequest resolveParameter(InvibesInternalParams invibesParams, BidRequest request) {
        final String buyeruid = request.getUser() != null ? request.getUser().getBuyeruid() : null;
        final String lid = StringUtils.isNotBlank(buyeruid) ? request.getUser().getBuyeruid() : StringUtils.EMPTY;

        if (request.getSite() == null) {
            throw new PreBidException("Site not specified");
        }

        return InvibesBidRequest.builder()
                .isTestBid(invibesParams.getTestBvid() != null
                        && !invibesParams.getTestBvid().equals(StringUtils.EMPTY))
                .bidParamsJson(mapper.encode(invibesParams.getBidParams()))
                .location(request.getSite().getPage())
                .lid(lid)
                .kw(request.getSite().getKeywords())
                .isAMP(invibesParams.getIsAMP())
                .width(resolveWidth(request.getDevice()))
                .height(resolveHeight(request.getDevice()))
                .gdprConsent(invibesParams.getGdprConsent())
                .gdpr(invibesParams.getGdpr())
                .bvid(invibesParams.getTestBvid())
                .invibBVLog(invibesParams.getTestLog())
                .videoAdDebug(invibesParams.getTestLog())
                .build();
    }

    private String resolveHeight(Device device) {
        final Integer height = device != null ? device.getH() : null;

        return height != null && height > NumberUtils.INTEGER_ZERO
                ? height.toString() : null;
    }

    private String resolveWidth(Device device) {
        final Integer width = device != null ? device.getW() : null;

        return width != null && width > NumberUtils.INTEGER_ZERO
                ? width.toString() : null;
    }

    private String makeUrl(Integer domainId) {
        final String host = resolveHost(domainId);
        final String url = endpointUrl.replace(URL_HOST_MACRO, host);
        try {
            HttpUtil.validateUrl(Objects.requireNonNull(url));
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }

        return url;
    }

    private String resolveHost(Integer domainId) {
        if (domainId == null) {
            return "bid.videostep.com";
        } else if (domainId >= 1002) {
            return String.format("bid%s.videostep.com", domainId - 1000);
        } else if (domainId == 1) {
            return "adweb.videostepstage.com";
        } else if (domainId == 2) {
            return "adweb.invibesstage.com";
        } else {
            return "bid.videostep.com";
        }
    }

    private InvibesInternalParams updateWithGDPRParams(InvibesInternalParams internalParams,
                                                       String consentString, Boolean gdprApplies) {
        return internalParams.toBuilder()
                .gdpr(gdprApplies)
                .gdprConsent(consentString)
                .build();
    }

    private List<String> getUpdatedPlacementIds(String placementId, List<String> placementIds) {
        final List<String> updatedPlacementIds = placementIds != null
                ? placementIds : new ArrayList<>();
        if (StringUtils.isNotEmpty(placementId)) {
            updatedPlacementIds.add(placementId.trim());
        }
        return updatedPlacementIds;
    }

    private Map<String, InvibesPlacementProperty> getUpdatedProperties(
            Map<String, InvibesPlacementProperty> properties, String placementId,
            String impId, List<Format> adFormats) {
        properties.put(placementId, InvibesPlacementProperty.builder()
                .impId(impId)
                .formats(adFormats)
                .build());

        return properties;
    }

    private List<Format> resolveAdFormats(Banner currentBanner) {
        if (currentBanner.getFormat() != null) {
            return currentBanner.getFormat();
        } else if (currentBanner.getW() != null && currentBanner.getH() != null) {
            return Collections.singletonList(Format.builder()
                    .w(currentBanner.getW())
                    .h(currentBanner.getH())
                    .build());
        }

        return Collections.emptyList();
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<InvibesBidRequest> httpCall, BidRequest bidRequest) {
        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }

        try {
            final InvibesBidderResponse bidResponse =
                    mapper.decodeValue(httpCall.getResponse().getBody(), InvibesBidderResponse.class);
            if (bidResponse != null && StringUtils.isNotEmpty(bidResponse.getError())) {
                return Result.emptyWithError(
                        BidderError.badServerResponse(String.format("Server error: %s.", bidResponse.getError())));
            }
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(InvibesBidRequest bidRequest, InvibesBidderResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getTypedBids())) {
            return Collections.emptyList();
        }

        return bidsFromResponse(bidRequest, bidResponse);
    }

    private List<BidderBid> bidsFromResponse(InvibesBidRequest bidRequest, InvibesBidderResponse bidResponse) {
        return bidResponse.getTypedBids().stream()
                .filter(Objects::nonNull)
                .map(InvibesTypedBid::getBid)
                .filter(Objects::nonNull)
                //TODO add DealPriority
                .map(bid -> BidderBid.of(bid, BidType.banner, bidResponse.getCurrency()))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
