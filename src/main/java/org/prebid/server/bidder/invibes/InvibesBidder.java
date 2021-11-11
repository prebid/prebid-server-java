package org.prebid.server.bidder.invibes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.prebid.server.proto.openrtb.ext.request.invibes.model.InvibesDebug;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
        if (request.getSite() == null) {
            return Result.withError(BidderError.badInput("Site not specified"));
        }

        final List<BidderError> errors = new ArrayList<>();

        final String consentString = resolveConsentString(request.getUser());
        final Boolean gdprApplies = resolveGDPRApplies(request.getRegs());

        final InvibesInternalParams invibesInternalParams = new InvibesInternalParams();
        invibesInternalParams.setBidParams(InvibesBidParams.builder()
                .properties(new HashMap<>())
                .placementIds(new ArrayList<>())
                .bidVersion(INVIBES_BID_VERSION)
                .build());

        for (Imp imp : request.getImp()) {
            final ExtImpInvibes extImpInvibes;
            try {
                extImpInvibes = parseImpExt(imp);
                validateImp(imp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }
            updateInvibesInternalParams(invibesInternalParams, extImpInvibes, imp);
        }

        //TODO: add AMP parameter to invibesInternalParams, after reqInfo will be implemented

        final List<String> placementIds = invibesInternalParams.getBidParams().getPlacementIds();
        if (CollectionUtils.isEmpty(placementIds)) {
            return Result.of(Collections.emptyList(), errors);
        }

        invibesInternalParams.setGdpr(gdprApplies);
        invibesInternalParams.setGdprConsent(consentString);

        try {
            final HttpRequest<InvibesBidRequest> httpRequest = makeRequest(invibesInternalParams, request);
            return Result.of(Collections.singletonList(httpRequest), errors);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }
    }

    private ExtImpInvibes parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), INVIBES_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(
                    String.format("Error parsing invibesExt parameters in impression with id: %s", imp.getId()));
        }
    }

    private void validateImp(Imp imp) {
        if (imp.getBanner() == null) {
            throw new PreBidException(String.format("Banner not specified in impression with id: %s", imp.getId()));
        }
    }

    private String resolveConsentString(User user) {
        final ExtUser extUser = user != null ? user.getExt() : null;
        return extUser != null ? extUser.getConsent() : "";
    }

    private Boolean resolveGDPRApplies(Regs regs) {
        final ExtRegs extRegs = regs != null ? regs.getExt() : null;
        final Integer gdpr = extRegs != null ? extRegs.getGdpr() : null;

        return gdpr == null || gdpr == 1;
    }

    private void updateInvibesInternalParams(InvibesInternalParams invibesInternalParams,
                                             ExtImpInvibes invibesExt,
                                             Imp imp) {
        final String impExtPlacementId = invibesExt.getPlacementId();
        final InvibesBidParams bidParams = invibesInternalParams.getBidParams();
        final List<String> updatedPlacementIds = bidParams.getPlacementIds();

        if (StringUtils.isNotBlank(impExtPlacementId)) {
            updatedPlacementIds.add(impExtPlacementId.trim());
        }
        final Banner banner = imp.getBanner();
        final List<Format> adFormats = resolveAdFormats(banner);

        bidParams.getProperties()
                .put(impExtPlacementId, InvibesPlacementProperty.builder()
                        .impId(imp.getId())
                        .formats(adFormats)
                        .build());

        final InvibesBidParams updatedBidParams = bidParams.toBuilder()
                .placementIds(updatedPlacementIds)
                .build();

        invibesInternalParams.setDomainId(invibesExt.getDomainId());
        invibesInternalParams.setBidParams(updatedBidParams);

        final InvibesDebug invibesDebug = invibesExt.getDebug();
        final String invibesDebugTestBvid = invibesDebug != null ? invibesDebug.getTestBvid() : null;
        if (StringUtils.isNotBlank(invibesDebugTestBvid)) {
            invibesInternalParams.setTestBvid(invibesDebugTestBvid);
        }

        if (invibesDebug != null) {
            invibesInternalParams.setTestLog(invibesDebug.getTestLog());
        }
    }

    private List<Format> resolveAdFormats(Banner currentBanner) {
        if (currentBanner.getFormat() != null) {
            return currentBanner.getFormat();
        } else {
            final Integer formatW = currentBanner.getW();
            final Integer formatH = currentBanner.getH();
            return formatW != null && formatH != null
                    ? Collections.singletonList(Format.builder().w(formatW).h(formatH).build())
                    : Collections.emptyList();
        }
    }

    private HttpRequest<InvibesBidRequest> makeRequest(InvibesInternalParams invibesParams,
                                                       BidRequest request) {
        final String host = resolveHost(invibesParams.getDomainId());
        final String url = endpointUrl.replace(URL_HOST_MACRO, host);
        final InvibesBidRequest parameter = resolveParameter(invibesParams, request);

        final Device device = request.getDevice();
        final Site site = request.getSite();
        final MultiMap headers = resolveHeaders(device, site);

        return HttpRequest.<InvibesBidRequest>builder()
                .method(HttpMethod.POST)
                .uri(url)
                .headers(headers)
                .payload(parameter)
                .body(mapper.encodeToBytes(parameter))
                .build();
    }

    private InvibesBidRequest resolveParameter(InvibesInternalParams invibesParams, BidRequest request) {
        final User user = request.getUser();
        final String buyeruid = user != null ? user.getBuyeruid() : null;
        final String lid = StringUtils.isNotBlank(buyeruid) ? buyeruid : "";

        return createRequest(invibesParams, lid, request.getDevice(), request.getSite());
    }

    private InvibesBidRequest createRequest(InvibesInternalParams invibesParams, String lid,
                                            Device device, Site site) {
        final String testBvid = invibesParams.getTestBvid();
        final Boolean testLog = invibesParams.getTestLog();

        return InvibesBidRequest.builder()
                .isTestBid(StringUtils.isNotBlank(testBvid))
                .bidParamsJson(mapper.encodeToString(invibesParams.getBidParams()))
                .location(site.getPage())
                .lid(lid)
                .kw(site.getKeywords())
                .isAmp(invibesParams.getIsAmp())
                .width(resolveWidth(device))
                .height(resolveHeight(device))
                .gdprConsent(invibesParams.getGdprConsent())
                .gdpr(invibesParams.getGdpr())
                .bvid(testBvid)
                .invibBVLog(testLog)
                .videoAdDebug(testLog)
                .build();
    }

    private static String resolveHeight(Device device) {
        final Integer height = device != null ? device.getH() : null;

        return height != null && height > 0 ? height.toString() : null;
    }

    private static String resolveWidth(Device device) {
        final Integer width = device != null ? device.getW() : null;

        return width != null && width > 0 ? width.toString() : null;
    }

    private static String resolveHost(Integer domainId) {
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

    private static MultiMap resolveHeaders(Device device, Site site) {
        final MultiMap headers = HttpUtil.headers();
        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIpv6());
        }
        if (site != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.REFERER_HEADER, site.getPage());
        }
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, "Aver", ADAPTER_VERSION);
        return headers;
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<InvibesBidRequest> httpCall, BidRequest bidRequest) {
        try {
            final InvibesBidderResponse bidResponse =
                    mapper.decodeValue(httpCall.getResponse().getBody(), InvibesBidderResponse.class);
            if (bidResponse != null && StringUtils.isNotBlank(bidResponse.getError())) {
                return Result.withError(
                        BidderError.badServerResponse(String.format("Server error: %s.", bidResponse.getError())));
            }
            return Result.of(extractBids(bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(InvibesBidderResponse bidResponse) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getTypedBids())
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse);
    }

    private List<BidderBid> bidsFromResponse(InvibesBidderResponse bidResponse) {
        return bidResponse.getTypedBids().stream()
                .filter(Objects::nonNull)
                .map(InvibesTypedBid::getBid)
                .filter(Objects::nonNull)
                //TODO add DealPriority
                .map(bid -> BidderBid.of(bid, BidType.banner, bidResponse.getCurrency()))
                .collect(Collectors.toList());
    }
}
