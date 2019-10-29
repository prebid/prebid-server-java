package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.video.BidRequestVideo;
import com.iab.openrtb.request.video.IncludeBrandCategory;
import com.iab.openrtb.request.video.Pod;
import com.iab.openrtb.request.video.Podconfig;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.proto.openrtb.ext.ExtIncludeBrandCategory;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtCurrency;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheBids;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class VideoRequestFactory {

    private static final String STORED_ID_REQUEST_PARAM = "storedrequestid";
    private static final String DEBUG_REQUEST_PARAM = "debug";
    private static final String OW_REQUEST_PARAM = "ow";
    private static final String OH_REQUEST_PARAM = "oh";
    private static final String W_REQUEST_PARAM = "w";
    private static final String H_REQUEST_PARAM = "h";
    private static final String MS_REQUEST_PARAM = "ms";
    private static final String CURL_REQUEST_PARAM = "curl";
    private static final String SLOT_REQUEST_PARAM = "slot";
    private static final String TIMEOUT_REQUEST_PARAM = "timeout";
    private static final String GDPR_CONSENT_PARAM = "gdpr_consent";
    private static final int NO_LIMIT_SPLIT_MODE = -1;
    private static final Long DEFAULT_TMAX = 5000L;

    private final StoredRequestProcessor storedRequestProcessor;
    private final AuctionRequestFactory auctionRequestFactory;
    private final TimeoutResolver timeoutResolver;
    private boolean videoStoredRequestRequired;

    public VideoRequestFactory(StoredRequestProcessor storedRequestProcessor,
                               AuctionRequestFactory auctionRequestFactory, TimeoutResolver timeoutResolver,
                               boolean videoStoredRequestRequired) {

        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.auctionRequestFactory = Objects.requireNonNull(auctionRequestFactory);
        this.timeoutResolver = Objects.requireNonNull(timeoutResolver);
        this.videoStoredRequestRequired = videoStoredRequestRequired;
    }

    /**
     * Creates {@link AuctionContext} based on {@link RoutingContext}.
     */
    public Future<AuctionContext> fromRequest(RoutingContext routingContext, long startTime) {
        final String tagId = routingContext.request().getParam(STORED_ID_REQUEST_PARAM);
        if (StringUtils.isBlank(tagId)) {
            return Future.failedFuture(new InvalidRequestException("Unable to find required stored request id"));
        }

        return createBidRequest(routingContext, tagId)
                .compose(bidRequest ->
                        auctionRequestFactory.toAuctionContext(routingContext, bidRequest, startTime, timeoutResolver));
    }

    /**
     * Creates {@link BidRequest} and sets properties which were not set explicitly by the client, but can be
     * updated by values derived from headers and other request attributes.
     */
    private Future<BidRequest> createBidRequest(RoutingContext context, String storedVideoId) {
        return storedRequestProcessor.processVideoRequest(storedVideoId, context)
                .map(this::validateStoredBidRequest)
                //TODO HANDLE ERROR
                .map(bidRequestVideo -> Tuple2.of(bidRequestVideo, mergeWithDefaultBidRequest(bidRequestVideo)))


                .map(bidRequest -> fillExplicitParameters(bidRequest, context))
                .map(bidRequest -> overrideParameters(bidRequest, context.request()))

                .map(bidRequest -> auctionRequestFactory.fillImplicitParameters(bidRequest, context, timeoutResolver))
                .map(auctionRequestFactory::validateRequest);
    }

    //Should be called only after validat
    private static BidRequest mergeWithDefaultBidRequest(BidRequestVideo videoRequest) {
        final BidRequest.BidRequestBuilder bidRequestBuilder = defaultBidRequest().toBuilder();

        final Site site = videoRequest.getSite();
        if (site != null) {
            final Site.SiteBuilder siteBuilder = site.toBuilder();
            final Content content = videoRequest.getContent();
            if (content != null) {
                siteBuilder.content(content);
            }
            siteBuilder.content(content);
            bidRequestBuilder.site(siteBuilder.build());
        }

        final App app = videoRequest.getApp();
        if (app != null) {
            final App.AppBuilder appBuilder = app.toBuilder();
            final Content content = videoRequest.getContent();
            if (content != null) {
                appBuilder.content(content);
            }
            appBuilder.content(content);
            bidRequestBuilder.app(appBuilder.build());
        }

        final Device device = videoRequest.getDevice();
        if (device != null) {
            bidRequestBuilder.device(device);
        }

        final User user = videoRequest.getUser();
        if (user != null) {
            final User updatedUser = User.builder()
                    .buyeruid("appnexus")
                    .yob(user.getYob())
                    .gender(user.getGender())
                    .keywords(user.getKeywords())
                    .build();
            bidRequestBuilder.user(updatedUser);
        }

        final List<String> bcat = videoRequest.getBcat();
        if (CollectionUtils.isNotEmpty(bcat)) {
            bidRequestBuilder.bcat(bcat);
        }

        final List<String> badv = videoRequest.getBadv();
        if (CollectionUtils.isNotEmpty(badv)) {
            bidRequestBuilder.badv(badv);
        }


        final ObjectNode ext = createBidExtension(videoRequest);
        bidRequestBuilder
                .ext(ext)
                .test(videoRequest.getTest());


        final Long videoTmax = videoRequest.getTmax();
        if (videoTmax == null || videoTmax == 0  ) {
            bidRequestBuilder.tmax(DEFAULT_TMAX);
        } else{
            bidRequestBuilder.tmax(videoRequest.getTmax());
        }
        //TODO
        return bidRequestBuilder.build();
    }



    private static ObjectNode createBidExtension(BidRequestVideo videoRequest) {
        final IncludeBrandCategory includebrandcategory = videoRequest.getIncludebrandcategory();
        final ExtIncludeBrandCategory extIncludeBrandCategory;
        if (includebrandcategory != null) {
            extIncludeBrandCategory = ExtIncludeBrandCategory.of(
                    includebrandcategory.getPrimaryAdserver(), includebrandcategory.getPublisher(), true);
        } else {
            extIncludeBrandCategory = ExtIncludeBrandCategory.of(null, null, false);
        }


        List<Integer> durationRangeSec = null;
        if (BooleanUtils.isFalse(videoRequest.getPodconfig().getRequireExactDuration())) {
            durationRangeSec = videoRequest.getPodconfig().getDurationRangeSec();
        }

        PriceGranularity priceGranularity = PriceGranularity.createFromString("med");
        final Integer precision = videoRequest.getPriceGranularity().getPrecision();
        if (precision != null && precision != 0) {
            priceGranularity = videoRequest.getPriceGranularity();
        }

        final ExtRequestTargeting targeting = ExtRequestTargeting.of(Json.mapper.valueToTree(priceGranularity), null, null, true, extIncludeBrandCategory, durationRangeSec, null);

        final ExtRequestPrebidCache extReqPrebidCache = ExtRequestPrebidCache.of(null, ExtRequestPrebidCacheVastxml.of(null, null), null);

        final ExtRequestPrebid extRequestPrebid = ExtRequestPrebid.builder()
                .cache(extReqPrebidCache)
                .targeting(targeting)
                .build();

        return Json.mapper.valueToTree(ExtBidRequest.of(extRequestPrebid));
    }

    private static BidRequest defaultBidRequest() {
        return BidRequest.builder().build();
    }

    /**
     * Throws {@link InvalidRequestException} in case of invalid {@link BidRequest}.
     */
    private BidRequest validateStoredBidRequest(BidRequestVideo bidRequest) {
        final List<InvalidRequestException> errors = new ArrayList<>();

        if (videoStoredRequestRequired && StringUtils.isBlank(bidRequest.getStoredrequestid())) {
            errors.add(new InvalidRequestException("request missing required field: storedrequestid"));
        }
        final Podconfig podconfig = bidRequest.getPodconfig();
        if (podconfig == null) {
            errors.add(new InvalidRequestException("request missing required field: PodConfig"));
        } else {
            final List<Pod> pods = podconfig.getPods();
            if (CollectionUtils.isEmpty(pods)) {
                errors.add(new InvalidRequestException("request missing required field: PodConfig.DurationRangeSec"));
            }
            if (isZeroOrNegativeDuration(podconfig.getDurationRangeSec())) {
                errors.add(new InvalidRequestException("duration array cannot contain negative or zero values"));
            }
            if (CollectionUtils.sizeIsEmpty(pods)) {
                errors.add(new InvalidRequestException("request missing required field: PodConfig.Pods"));
            }

            final List<PodError> podErrors = validateEachPod(pods);




            final Site site = bidRequest.getSite();
            final App app = bidRequest.getApp();
            if (app == null && site == null) {
                errors.add(new InvalidRequestException("request missing required field: site or app"));
            } else if (app != null && site != null) {
                errors.add(new InvalidRequestException("request.site or request.app must be defined, but not both"));
            } else if (site != null && StringUtils.isBlank(site.getId()) && StringUtils.isBlank(site.getPage())) {
                errors.add(new InvalidRequestException("request.site missing required field: id or page"));
            } else if (app != null) {
                if (StringUtils.isBlank(app.getId())) {
                    if _, found := deps.cfg.BlacklistedAppMap[req.App.ID]; found {
                        err := &errortypes.BlacklistedApp{Message: fmt.Sprintf("Prebid-server does not process requests from App ID: %s", req.App.ID)}
                        return errL, podErrors
                    }
                } else {
                    if req.App.Bundle == "" {
                        err := errors.New("request.app missing required field: id or bundle")
                    }
                }
            }

            if len(req.Video.Mimes) == 0 {
                err := errors.New("request missing required field: Video.Mimes")
                errL = append(errL, err)
            } else {
                mimes := make([]string, 0, 0)
                for _, mime := range req.Video.Mimes {
                    if mime != "" {
                        mimes = append(mimes, mime)
                    }
                }
                if len(mimes) == 0 {
                    err := errors.New("request missing required field: Video.Mimes, mime types contains empty strings only")
                    errL = append(errL, err)
                }
                if len(mimes) > 0 {
                    req.Video.Mimes = mimes
                }
            }

            if len(req.Video.Protocols) == 0 {
                err := errors.New("request missing required field: Video.Protocols")
                errL = append(errL, err)
            }

    }

    private static List<PodError> validateEachPod(List<Pod> pods) {
        final List<PodError> podErrorsResult = new ArrayList<>();

        final Map<Integer, Boolean> podIdToFlag = new HashMap<>();
        for (int i = 0; i < pods.size(); i++) {
            final List<InvalidRequestException> podErrors = new ArrayList<>();
            final Pod pod = pods.get(i);

            final Integer podId = pod.getPodId();
            if (podIdToFlag.get(podId)) {
                podErrors.add(new InvalidRequestException(
                        "request duplicated required field: PodConfig.Pods.PodId, Pod id: " + podId));
            } else {
                podIdToFlag.put(podId, true);
            }
            if (podId == null || podId <= 0) {
                podErrors.add(new InvalidRequestException(
                        "request missing required field: PodConfig.Pods.PodId, Pod index: " + i));
            }
            final Integer adpodDurationSec = pod.getAdpodDurationSec();
            if (adpodDurationSec != null) {
                if (adpodDurationSec == 0) {
                    podErrors.add(new InvalidRequestException(
                            "request missing or incorrect required field: PodConfig.Pods.AdPodDurationSec, Pod index: "
                                    + i));
                }
                if (adpodDurationSec < 0) {
                    podErrors.add(new InvalidRequestException(
                            "request incorrect required field: PodConfig.Pods.AdPodDurationSec is negative, Pod index: "
                                    + i));
                }
            } else {
                podErrors.add(new InvalidRequestException(
                        "request missing or incorrect required field: PodConfig.Pods.AdPodDurationSec, Pod index: "
                                + i));
            }
            if (StringUtils.isBlank(pod.getConfigId())) {
                podErrors.add(new InvalidRequestException(
                        "request missing or incorrect required field: PodConfig.Pods.ConfigId, Pod index: " + i));
            }
            if (!podErrors.isEmpty()) {
                podErrorsResult.add(PodError.of(podId, i, podErrors));
            }
        }
        return podErrorsResult;
    }

    private static boolean isZeroOrNegativeDuration(List<Integer> durationRangeSec) {
        if (durationRangeSec == null) {
            return false;
        }

        return durationRangeSec.stream()
                .anyMatch(duration -> duration <= 0);
    }

    /**
     * Updates {@link BidRequest}.ext.prebid.targeting and {@link BidRequest}.ext.prebid.cache.bids with default values
     * if it was not included by user. Updates {@link Imp} security if required to ensure that amp always uses
     * https protocol. Sets {@link BidRequest}.test = 1 if it was passed in {@link RoutingContext}.
     */
    private static BidRequest fillExplicitParameters(BidRequest bidRequest, RoutingContext context) {
        final List<Imp> imps = bidRequest.getImp();
        // Force HTTPS as AMP requires it, but pubs can forget to set it.
        final Imp imp = imps.get(0);
        final Integer secure = imp.getSecure();
        final boolean setSecure = secure == null || secure != 1;

        final ExtBidRequest extBidRequest = extBidRequest(bidRequest.getExt());
        final ExtRequestPrebid prebid = extBidRequest.getPrebid();

        // AMP won't function unless ext.prebid.targeting and ext.prebid.cache.bids are defined.
        // If the user didn't include them, default those here.
        final boolean setDefaultTargeting;
        final boolean setDefaultCache;

        if (prebid == null) {
            setDefaultTargeting = true;
            setDefaultCache = true;
        } else {
            final ExtRequestTargeting targeting = prebid.getTargeting();
            setDefaultTargeting = targeting == null
                    || targeting.getIncludewinners() == null
                    || targeting.getIncludebidderkeys() == null
                    || targeting.getPricegranularity() == null || targeting.getPricegranularity().isNull();
            final ExtRequestPrebidCache cache = prebid.getCache();
            setDefaultCache = cache == null || cache.equals(ExtRequestPrebidCache.EMPTY);
        }

        final Integer debugQueryParam = debugFromQueryStringParam(context);

        final Integer test = bidRequest.getTest();
        final Integer updatedTest = debugQueryParam != null && !Objects.equals(debugQueryParam, test)
                ? debugQueryParam
                : null;

        final Integer debug = prebid != null ? prebid.getDebug() : null;
        final Integer updatedDebug = debugQueryParam != null && !Objects.equals(debugQueryParam, debug)
                ? debugQueryParam
                : null;

        final BidRequest result;
        if (setSecure || setDefaultTargeting || setDefaultCache || updatedTest != null || updatedDebug != null) {
            result = bidRequest.toBuilder()
                    .imp(setSecure ? Collections.singletonList(imps.get(0).toBuilder().secure(1).build()) : imps)
                    .test(ObjectUtils.defaultIfNull(updatedTest, test))
                    .ext(extBidRequestNode(bidRequest, prebid, setDefaultTargeting, setDefaultCache, updatedDebug))
                    .build();
        } else {
            result = bidRequest;
        }
        return result;
    }

    /**
     * Extracts {@link ExtBidRequest} from bidrequest.ext {@link ObjectNode}.
     */
    private static ExtBidRequest extBidRequest(ObjectNode extBidRequestNode) {
        try {
            return Json.mapper.treeToValue(extBidRequestNode, ExtBidRequest.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format("Error decoding bidRequest.ext: %s", e.getMessage()));
        }
    }

    /**
     * Returns debug flag from request query string if it is equal to either 0 or 1, or null if otherwise.
     */
    private static Integer debugFromQueryStringParam(RoutingContext context) {
        final String debug = context.request().getParam(DEBUG_REQUEST_PARAM);
        return Objects.equals(debug, "1") ? Integer.valueOf(1) : Objects.equals(debug, "0") ? 0 : null;
    }

    /**
     * Extracts parameters from http request and overrides corresponding attributes in {@link BidRequest}.
     */
    private static BidRequest overrideParameters(BidRequest bidRequest, HttpServerRequest request) {
        final Site updatedSite = overrideSite(site, request);
        final Imp updatedImp = overrideImp(bidRequest.getImp().get(0), request);
        final Long updatedTimeout = overrideTimeout(bidRequest.getTmax(), request);
        final User updatedUser = overrideUser(bidRequest.getUser(), request);

        final BidRequest result;
        if (updatedSite != null || updatedImp != null || updatedTimeout != null || updatedUser != null) {
            result = bidRequest.toBuilder()
                    .site(updatedSite != null ? updatedSite : site)
                    .imp(updatedImp != null ? Collections.singletonList(updatedImp) : bidRequest.getImp())
                    .tmax(updatedTimeout != null ? updatedTimeout : bidRequest.getTmax())
                    .user(updatedUser != null ? updatedUser : bidRequest.getUser())
                    .build();
        } else {
            result = bidRequest;
        }
        return result;
    }

    private static Site overrideSite(Site site, HttpServerRequest request) {
        final String canonicalUrl = canonicalUrl(request);

        final ObjectNode siteExt = site != null ? site.getExt() : null;
        final boolean shouldSetExtAmp = siteExt == null || siteExt.get("amp") == null;

        if (StringUtils.isNotBlank(canonicalUrl) || shouldSetExtAmp) {
            final Site.SiteBuilder siteBuilder = site == null ? Site.builder() : site.toBuilder();
            if (StringUtils.isNotBlank(canonicalUrl)) {
                siteBuilder.page(canonicalUrl);
            }
            if (shouldSetExtAmp) {
                final ObjectNode data = siteExt != null ? (ObjectNode) siteExt.get("data") : null;
                siteBuilder.ext(Json.mapper.valueToTree(ExtSite.of(1, data)));
            }
            return siteBuilder.build();
        }
        return null;
    }

    private static String canonicalUrl(HttpServerRequest request) {
        try {
            return HttpUtil.decodeUrl(request.getParam(CURL_REQUEST_PARAM));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Imp overrideImp(Imp imp, HttpServerRequest request) {
        final String tagId = request.getParam(SLOT_REQUEST_PARAM);
        final Banner banner = imp.getBanner();
        final List<Format> overwrittenFormats = banner != null
                ? createOverrideBannerFormats(request, banner.getFormat())
                : null;
        if (StringUtils.isNotBlank(tagId) || CollectionUtils.isNotEmpty(overwrittenFormats)) {
            return imp.toBuilder()
                    .tagid(StringUtils.isNotBlank(tagId) ? tagId : imp.getTagid())
                    .banner(overrideBanner(imp.getBanner(), overwrittenFormats))
                    .build();
        }
        return null;
    }

    /**
     * Creates formats from request parameters to override origin amp banner formats.
     */
    private static List<Format> createOverrideBannerFormats(HttpServerRequest request, List<Format> formats) {
        final int overrideWidth = parseIntParamOrZero(request, OW_REQUEST_PARAM);
        final int width = parseIntParamOrZero(request, W_REQUEST_PARAM);
        final int overrideHeight = parseIntParamOrZero(request, OH_REQUEST_PARAM);
        final int height = parseIntParamOrZero(request, H_REQUEST_PARAM);
        final String multiSizeParam = request.getParam(MS_REQUEST_PARAM);

        final List<Format> paramsFormats = createFormatsFromParams(overrideWidth, width, overrideHeight, height,
                multiSizeParam);

        return CollectionUtils.isNotEmpty(paramsFormats)
                ? paramsFormats
                : updateFormatsFromParams(formats, width, height);
    }

    private static Integer parseIntParamOrZero(HttpServerRequest request, String name) {
        return parseIntOrZero(request.getParam(name));
    }

    private static Integer parseIntOrZero(String param) {
        try {
            return Integer.parseInt(param);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Create new formats from request parameters.
     */
    private static List<Format> createFormatsFromParams(Integer overrideWidth, Integer width, Integer overrideHeight,
                                                        Integer height, String multiSizeParam) {
        final List<Format> formats = new ArrayList<>();

        if (overrideWidth != 0 && overrideHeight != 0) {
            formats.add(Format.builder().w(overrideWidth).h(overrideHeight).build());
        } else if (overrideWidth != 0 && height != 0) {
            formats.add(Format.builder().w(overrideWidth).h(height).build());
        } else if (width != 0 && overrideHeight != 0) {
            formats.add(Format.builder().w(width).h(overrideHeight).build());
        } else if (width != 0 && height != 0) {
            formats.add(Format.builder().w(width).h(height).build());
        }

        // Append formats from multi-size param if exist
        final List<Format> multiSizeFormats = StringUtils.isNotBlank(multiSizeParam)
                ? parseMultiSizeParam(multiSizeParam)
                : Collections.emptyList();
        if (!multiSizeFormats.isEmpty()) {
            formats.addAll(multiSizeFormats);
        }

        return formats;
    }

    /**
     * Updates origin amp banner formats from parameters.
     */
    private static List<Format> updateFormatsFromParams(List<Format> formats, Integer width, Integer height) {
        final List<Format> updatedFormats;
        if (width != 0) {
            updatedFormats = formats.stream()
                    .map(format -> Format.builder().w(width).h(format.getH()).build())
                    .collect(Collectors.toList());
        } else if (height != 0) {
            updatedFormats = formats.stream()
                    .map(format -> Format.builder().w(format.getW()).h(height).build())
                    .collect(Collectors.toList());
        } else {
            updatedFormats = Collections.emptyList();
        }
        return updatedFormats;
    }

    private static Banner overrideBanner(Banner banner, List<Format> formats) {
        return banner != null && CollectionUtils.isNotEmpty(formats)
                ? banner.toBuilder().format(formats).build()
                : banner;
    }

    private static Long overrideTimeout(Long tmax, HttpServerRequest request) {
        final String timeoutQueryParam = request.getParam(TIMEOUT_REQUEST_PARAM);
        if (timeoutQueryParam == null) {
            return null;
        }

        final long timeout;
        try {
            timeout = Long.parseLong(timeoutQueryParam);
        } catch (NumberFormatException e) {
            return null;
        }

        return timeout > 0 && !Objects.equals(timeout, tmax) ? timeout : null;
    }

    private static User overrideUser(User user, HttpServerRequest request) {
        final String gdprConsent = request.getParam(GDPR_CONSENT_PARAM);
        if (StringUtils.isBlank(gdprConsent)) {
            return null;
        }

        final boolean hasUser = user != null;
        final ObjectNode extUserNode = hasUser ? user.getExt() : null;

        final ExtUser.ExtUserBuilder extUserBuilder = extUserNode != null
                ? extractExtUser(extUserNode).toBuilder()
                : ExtUser.builder();

        final ExtUser updatedExtUser = extUserBuilder.consent(gdprConsent).build();

        final User.UserBuilder userBuilder = hasUser ? user.toBuilder() : User.builder();

        return userBuilder
                .ext(Json.mapper.valueToTree(updatedExtUser))
                .build();
    }

    /**
     * Extracts {@link ExtUser} from bidrequest.user.ext {@link ObjectNode}.
     */
    private static ExtUser extractExtUser(ObjectNode extUserNode) {
        try {
            return Json.mapper.treeToValue(extUserNode, ExtUser.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format("Error decoding bidRequest.user.ext: %s", e.getMessage()));
        }
    }

    private static List<Format> parseMultiSizeParam(String ms) {
        final String[] formatStrings = ms.split(",", NO_LIMIT_SPLIT_MODE);
        final List<Format> formats = new ArrayList<>();
        for (String format : formatStrings) {
            final String[] widthHeight = format.split("x", NO_LIMIT_SPLIT_MODE);
            if (widthHeight.length != 2) {
                return Collections.emptyList();
            }

            final Integer width = parseIntOrZero(widthHeight[0]);
            final Integer height = parseIntOrZero(widthHeight[1]);

            if (width == 0 && height == 0) {
                return Collections.emptyList();
            }

            formats.add(Format.builder()
                    .w(width)
                    .h(height)
                    .build());
        }
        return formats;
    }

    /**
     * Creates updated bidrequest.ext {@link ObjectNode}.
     */
    private static ObjectNode extBidRequestNode(BidRequest bidRequest, ExtRequestPrebid prebid,
                                                boolean setDefaultTargeting, boolean setDefaultCache,
                                                Integer updatedDebug) {
        final ObjectNode result;
        if (setDefaultTargeting || setDefaultCache || updatedDebug != null) {
            final ExtRequestPrebid.ExtRequestPrebidBuilder prebidBuilder = prebid != null
                    ? prebid.toBuilder()
                    : ExtRequestPrebid.builder();

            if (setDefaultTargeting) {
                prebidBuilder.targeting(createTargetingWithDefaults(prebid));
            }
            if (setDefaultCache) {
                prebidBuilder.cache(ExtRequestPrebidCache.of(ExtRequestPrebidCacheBids.of(null, null),
                        ExtRequestPrebidCacheVastxml.of(null, null), null));
            }
            if (updatedDebug != null) {
                prebidBuilder.debug(updatedDebug);
            }

            result = Json.mapper.valueToTree(ExtBidRequest.of(prebidBuilder.build()));
        } else {
            result = bidRequest.getExt();
        }
        return result;
    }

    /**
     * Creates updated with default values bidrequest.ext.targeting {@link ExtRequestTargeting} if at least one of it's
     * child properties is missed or entire targeting does not exist.
     */
    private static ExtRequestTargeting createTargetingWithDefaults(ExtRequestPrebid prebid) {
        final ExtRequestTargeting targeting = prebid != null ? prebid.getTargeting() : null;
        final boolean isTargetingNull = targeting == null;

        final JsonNode priceGranularityNode = isTargetingNull ? null : targeting.getPricegranularity();
        final boolean isPriceGranularityNull = priceGranularityNode == null || priceGranularityNode.isNull();
        final JsonNode outgoingPriceGranularityNode = isPriceGranularityNull
                ? Json.mapper.valueToTree(ExtPriceGranularity.from(PriceGranularity.DEFAULT))
                : priceGranularityNode;

        final ExtMediaTypePriceGranularity mediaTypePriceGranularity = isTargetingNull
                ? null : targeting.getMediatypepricegranularity();

        final ExtCurrency currency = isTargetingNull ? null : targeting.getCurrency();

        final boolean includeWinners = isTargetingNull || targeting.getIncludewinners() == null
                || targeting.getIncludewinners();

        final boolean includeBidderKeys = isTargetingNull || targeting.getIncludebidderkeys() == null
                || targeting.getIncludebidderkeys();

        return ExtRequestTargeting.of(outgoingPriceGranularityNode, mediaTypePriceGranularity, currency,
                includeWinners, includeBidderKeys);
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static class PodError {
        Integer podId;
        Integer podIndex;
        List<InvalidRequestException> podErrors;
    }
}
