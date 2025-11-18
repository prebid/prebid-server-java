package org.prebid.server.auction.requestfactory;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.DebugResolver;
import org.prebid.server.auction.GeoLocationServiceWrapper;
import org.prebid.server.auction.ImplicitParametersExtractor;
import org.prebid.server.auction.InterstitialProcessor;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.auction.externalortb.ProfilesProcessor;
import org.prebid.server.auction.externalortb.StoredRequestProcessor;
import org.prebid.server.auction.gpp.AuctionGppService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.AuctionStoredResult;
import org.prebid.server.auction.model.IpAddress;
import org.prebid.server.auction.privacy.contextfactory.AuctionPrivacyContextFactory;
import org.prebid.server.auction.versionconverter.BidRequestOrtbVersionConversionManager;
import org.prebid.server.bidadjustments.BidAdjustmentsEnricher;
import org.prebid.server.cookie.CookieDeprecationService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.model.Endpoint;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.request.ConsentedProvidersSettings;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredAuctionResponse;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.settings.model.Account;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class GetInterfaceRequestFactory {

    private static final String ENDPOINT = Endpoint.openrtb2_get_interface.value();

    private final Ortb2RequestFactory ortb2RequestFactory;
    private final StoredRequestProcessor storedRequestProcessor;
    private final ProfilesProcessor profilesProcessor;
    private final BidRequestOrtbVersionConversionManager ortbVersionConversionManager;
    private final AuctionGppService gppService;
    private final CookieDeprecationService cookieDeprecationService;
    private final ImplicitParametersExtractor paramsExtractor;
    private final IpAddressHelper ipAddressHelper;
    private final Ortb2ImplicitParametersResolver paramsResolver;
    private final InterstitialProcessor interstitialProcessor;
    private final AuctionPrivacyContextFactory auctionPrivacyContextFactory;
    private final DebugResolver debugResolver;
    private final JacksonMapper mapper;
    private final GeoLocationServiceWrapper geoLocationServiceWrapper;
    private final BidAdjustmentsEnricher bidAdjustmentsEnricher;

    public GetInterfaceRequestFactory(Ortb2RequestFactory ortb2RequestFactory,
                                      StoredRequestProcessor storedRequestProcessor,
                                      ProfilesProcessor profilesProcessor,
                                      BidRequestOrtbVersionConversionManager ortbVersionConversionManager,
                                      AuctionGppService gppService,
                                      CookieDeprecationService cookieDeprecationService,
                                      ImplicitParametersExtractor paramsExtractor,
                                      IpAddressHelper ipAddressHelper,
                                      Ortb2ImplicitParametersResolver paramsResolver,
                                      InterstitialProcessor interstitialProcessor,
                                      AuctionPrivacyContextFactory auctionPrivacyContextFactory,
                                      DebugResolver debugResolver,
                                      JacksonMapper mapper,
                                      GeoLocationServiceWrapper geoLocationServiceWrapper,
                                      BidAdjustmentsEnricher bidAdjustmentsEnricher) {

        this.ortb2RequestFactory = Objects.requireNonNull(ortb2RequestFactory);
        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.profilesProcessor = Objects.requireNonNull(profilesProcessor);
        this.ortbVersionConversionManager = Objects.requireNonNull(ortbVersionConversionManager);
        this.gppService = Objects.requireNonNull(gppService);
        this.cookieDeprecationService = Objects.requireNonNull(cookieDeprecationService);
        this.paramsExtractor = Objects.requireNonNull(paramsExtractor);
        this.ipAddressHelper = Objects.requireNonNull(ipAddressHelper);
        this.paramsResolver = Objects.requireNonNull(paramsResolver);
        this.interstitialProcessor = Objects.requireNonNull(interstitialProcessor);
        this.auctionPrivacyContextFactory = Objects.requireNonNull(auctionPrivacyContextFactory);
        this.debugResolver = Objects.requireNonNull(debugResolver);
        this.mapper = Objects.requireNonNull(mapper);
        this.geoLocationServiceWrapper = Objects.requireNonNull(geoLocationServiceWrapper);
        this.bidAdjustmentsEnricher = Objects.requireNonNull(bidAdjustmentsEnricher);
    }

    public Future<AuctionContext> fromRequest(RoutingContext routingContext, long startTime) {
        final String body = routingContext.body().asString();

        final AuctionContext initialAuctionContext = ortb2RequestFactory.createAuctionContext(
                Endpoint.openrtb2_get_interface, MetricName.openrtb2web);

        return ortb2RequestFactory.executeEntrypointHooks(routingContext, body, initialAuctionContext)

                .map(httpRequest -> ortb2RequestFactory.enrichAuctionContext(
                        initialAuctionContext,
                        httpRequest,
                        initialBidRequest(httpRequest),
                        startTime))

                .compose(auctionContext -> ortb2RequestFactory.fetchAccount(auctionContext)
                        .map(auctionContext::with))

                .map(auctionContext -> auctionContext.with(removeTmpPublisher(auctionContext.getBidRequest())))

                .map(auctionContext -> auctionContext.with(debugResolver.debugContextFrom(auctionContext)))

                .compose(auctionContext -> storedRequestProcessor.processAuctionRequest(
                                auctionContext.getAccount().getId(), auctionContext.getBidRequest())
                        .map(AuctionStoredResult::bidRequest)
                        .map(auctionContext::with))

                .compose(auctionContext -> profilesProcessor.process(auctionContext, auctionContext.getBidRequest())
                        .map(auctionContext::with))

                .map(auctionContext -> auctionContext.with(completeBidRequest(
                        auctionContext.getBidRequest(),
                        auctionContext.getHttpRequest(),
                        auctionContext.getAccount())))

                .map(auctionContext -> auctionContext.with(requestTypeMetric(auctionContext.getBidRequest())))

                .recover(ortb2RequestFactory::restoreResultFromRejection);
    }

    public Future<AuctionContext> enrichAuctionContext(AuctionContext initialContext) {
        if (initialContext.isRequestRejected()) {
            return Future.succeededFuture(initialContext);
        }

        return Future.succeededFuture(initialContext)

                .compose(auctionContext -> geoLocationServiceWrapper.lookup(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> ortb2RequestFactory.enrichBidRequestWithGeolocationData(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> gppService.contextFrom(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> ortb2RequestFactory.activityInfrastructureFrom(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> updateAndValidateBidRequest(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> auctionPrivacyContextFactory.contextFrom(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> ortb2RequestFactory.enrichBidRequestWithAccountAndPrivacyData(auctionContext)
                        .map(auctionContext::with))

                .map(auctionContext -> auctionContext.with(bidAdjustmentsEnricher.enrichBidRequest(auctionContext)))

                .compose(auctionContext -> ortb2RequestFactory.executeProcessedAuctionRequestHooks(auctionContext)
                        .map(auctionContext::with))

                .map(ortb2RequestFactory::updateTimeout)

                .recover(ortb2RequestFactory::restoreResultFromRejection);
    }

    private BidRequest initialBidRequest(HttpRequestContext httpRequest) {
        final GetInterfaceParams params = new GetInterfaceParams(httpRequest);

        return BidRequest.builder()
                .imp(Collections.singletonList(initialImp(params)))
                .site(tmpSite(params)) // Temporarily add to fetch account
                .device(initialDevice(params))
                .user(initialUser(params))
                .tmax(params.tmax())
                .bcat(params.bCat())
                .badv(params.bAdv())
                .regs(initialRegs(params))
                .ext(initialExtRequest(params))
                .build();
    }

    private Imp initialImp(GetInterfaceParams params) {
        return Imp.builder()
                .tagid(params.tagId())
                .ext(mapper.mapper().valueToTree(ExtImpPrebid.builder()
                        .profiles(params.impProfiles())
                        .build()))
                .build();
    }

    private static Site tmpSite(GetInterfaceParams params) {
        return Site.builder()
                .publisher(Publisher.builder().id(params.accountId()).build())
                .build();
    }

    private static Device initialDevice(GetInterfaceParams params) {
        final IpAddress ipAddress = params.ip();
        return Device.builder()
                .dnt(params.dnt())
                .lmt(params.lmt())
                .ua(params.ua())
                .ip(ipAddress.getVersion() == IpAddress.IP.v4 ? ipAddress.getIp() : null)
                .ipv6(ipAddress.getVersion() == IpAddress.IP.v6 ? ipAddress.getIp() : null)
                .devicetype(params.deviceType())
                .ifa(params.ifa())
                .ext(ExtDevice.of(null, params.ifaType(), null))
                .build();
    }

    private static User initialUser(GetInterfaceParams params) {
        return User.builder()
                .ext(ExtUser.builder()
                        .consentedProvidersSettings(ConsentedProvidersSettings.of(params.consentedProviders()))
                        .build())
                .build();
    }

    private static Regs initialRegs(GetInterfaceParams params) {
        return Regs.builder()
                .coppa(params.coppa())
                .gdpr(params.gdpr())
                .usPrivacy(params.usPrivacy())
                .gppSid(params.gppSid())
                .ext(ExtRegs.of(null, null, params.gpc(), null))
                .build();
    }

    private static ExtRequest initialExtRequest(GetInterfaceParams params) {
        return ExtRequest.of(ExtRequestPrebid.builder()
                .debug(params.debug())
                .storedrequest(ExtStoredRequest.of(params.storedRequestId()))
                .profiles(params.requestProfiles())
                .storedAuctionResponse(ExtStoredAuctionResponse.of(
                        params.storedAuctionResponseId(), null, null))
                .outputFormat(params.outputFormat())
                .outputModule(params.outputModule())
                .build());
    }

    private static BidRequest removeTmpPublisher(BidRequest bidRequest) {
        return bidRequest.toBuilder().site(null).build();
    }

    private BidRequest completeBidRequest(BidRequest bidRequest,
                                          HttpRequestContext httpRequest,
                                          Account account) {

        final GetInterfaceParams params = new GetInterfaceParams(httpRequest);

        final Imp imp = bidRequest.getImp().getFirst();

        return bidRequest.toBuilder()
                .imp(Collections.singletonList(imp.toBuilder()
                        .banner(completeBanner(imp.getBanner(), params))
                        .video(completeVideo(imp.getVideo(), params))
                        .audio(completeAudio(imp.getAudio(), params))
                        .build()))
                .site(completeSite(bidRequest.getSite(), params, account))
                .app(completeApp(bidRequest.getApp(), params, account))
                .dooh(completeDooh(bidRequest.getDooh(), params, account))
                .build();
    }

    private static Banner completeBanner(Banner banner, GetInterfaceParams params) {
        if (banner == null) {
            return null;
        }

        return banner.toBuilder()
                .format(params.format())
                .w(params.w())
                .h(params.h())
                .btype(params.bType())
                .mimes(params.mimes())
                .battr(params.bAttr())
                .pos(params.pos())
                .topframe(params.topFrame())
                .expdir(params.expDir())
                .api(params.api())
                .build();
    }

    private static Video completeVideo(Video video, GetInterfaceParams params) {
        if (video == null) {
            return null;
        }

        return video.toBuilder()
                .w(params.w())
                .h(params.h())
                .mimes(params.mimes())
                .minduration(params.minDuration())
                .maxduration(params.maxDuration())
                .startdelay(params.startDelay())
                .maxseq(params.maxSeq())
                .poddur(params.podDur())
                .protocols(params.protocols())
                .podid(params.podId())
                .podseq(params.podSeq())
                .rqddurs(params.rqdDurs())
                .placement(params.placement())
                .plcmt(params.plcmt())
                .linearity(params.linearity())
                .skip(params.skip())
                .skipmin(params.skipMin())
                .skipafter(params.skipAfter())
                .sequence(params.sequence())
                .slotinpod(params.slotInPod())
                .mincpmpersec(params.minCpmPerSec())
                .battr(params.bAttr())
                .pos(params.pos())
                .maxextended(params.maxExtended())
                .minbitrate(params.minBitrate())
                .maxbitrate(params.maxBitrate())
                .boxingallowed(params.boxingAllowed())
                .playbackmethod(params.playbackMethod())
                .playbackend(params.playbackEnd())
                .delivery(params.delivery())
                .api(params.api())
                .build();
    }

    private static Audio completeAudio(Audio audio, GetInterfaceParams params) {
        if (audio == null) {
            return null;
        }

        return audio.toBuilder()
                .mimes(params.mimes())
                .minduration(params.minDuration())
                .maxduration(params.maxDuration())
                .startdelay(params.startDelay())
                .maxseq(params.maxSeq())
                .poddur(params.podDur())
                .protocols(params.protocols())
                .podid(params.podId())
                .podseq(params.podSeq())
                .rqddurs(params.rqdDurs())
                .sequence(params.sequence())
                .slotinpod(params.slotInPod())
                .mincpmpersec(params.minCpmPerSec())
                .battr(params.bAttr())
                .maxextended(params.maxExtended())
                .minbitrate(params.minBitrate())
                .maxbitrate(params.maxBitrate())
                .delivery(params.delivery())
                .api(params.api())
                .feed(params.feed())
                .stitched(params.stitched())
                .nvol(params.nvol())
                .build();
    }

    private static Site completeSite(Site site, GetInterfaceParams params, Account account) {
        if (site == null) {
            return null;
        }

        return site.toBuilder()
                .page(params.page())
                .publisher(completePublisher(site.getPublisher(), account))
                .content(completeContent(site.getContent(), params))
                .build();
    }

    private static App completeApp(App app, GetInterfaceParams params, Account account) {
        if (app == null) {
            return null;
        }

        return app.toBuilder()
                .name(params.name())
                .bundle(params.bundle())
                .storeurl(params.storeUrl())
                .publisher(completePublisher(app.getPublisher(), account))
                .content(completeContent(app.getContent(), params))
                .build();
    }

    private static Dooh completeDooh(Dooh dooh, GetInterfaceParams params, Account account) {
        if (dooh == null) {
            return null;
        }

        return dooh.toBuilder()
                .publisher(completePublisher(dooh.getPublisher(), account))
                .content(completeContent(dooh.getContent(), params))
                .build();
    }

    private static Publisher completePublisher(Publisher publisher, Account account) {
        return Optional.ofNullable(publisher)
                .map(Publisher::toBuilder)
                .orElseGet(Publisher::builder)
                .id(account.getId())
                .build();
    }

    private static Content completeContent(Content content, GetInterfaceParams params) {
        return Optional.ofNullable(content)
                .map(Content::toBuilder)
                .orElseGet(Content::builder)
                .title(params.title())
                .series(params.series())
                .genre(params.genre())
                .url(params.url())
                .cattax(params.catTax())
                .cat(params.cat())
                .contentrating(params.contentRating())
                .livestream(params.liveStream())
                .language(params.language())
                .build();
    }

    private Future<BidRequest> updateAndValidateBidRequest(AuctionContext auctionContext) {
        final Account account = auctionContext.getAccount();
        final HttpRequestContext httpRequest = auctionContext.getHttpRequest();
        final List<String> debugWarnings = auctionContext.getDebugWarnings();

        return updateBidRequest(auctionContext)
                .compose(bidRequest -> ortb2RequestFactory.limitImpressions(account, bidRequest, debugWarnings))
                .compose(bidRequest -> ortb2RequestFactory.validateRequest(
                        account, bidRequest, httpRequest, auctionContext.getDebugContext(), debugWarnings))
                .map(interstitialProcessor::process);
    }

    private Future<BidRequest> updateBidRequest(AuctionContext auctionContext) {
        return Future.succeededFuture(auctionContext.getBidRequest())
                .map(ortbVersionConversionManager::convertToAuctionSupportedVersion)
                .map(bidRequest -> gppService.updateBidRequest(bidRequest, auctionContext))
                .map(bidRequest -> paramsResolver.resolve(bidRequest, auctionContext, ENDPOINT, true))
                .map(bidRequest -> cookieDeprecationService.updateBidRequestDevice(bidRequest, auctionContext))
                .map(bidRequest -> ortb2RequestFactory.removeEmptyEids(bidRequest, auctionContext.getDebugWarnings()));
    }

    private static MetricName requestTypeMetric(BidRequest bidRequest) {
        if (bidRequest.getApp() != null) {
            return MetricName.openrtb2app;
        } else if (bidRequest.getDooh() != null) {
            return MetricName.openrtb2dooh;
        } else {
            return MetricName.openrtb2web;
        }
    }

    private class GetInterfaceParams {

        HttpRequestContext httpRequestContext;

        GetInterfaceParams(HttpRequestContext httpRequestContext) {
            this.httpRequestContext = Objects.requireNonNull(httpRequestContext);
        }

        public String storedRequestId() {
            return Optional.ofNullable(getString("srid"))
                    .orElseGet(() -> getString("tag_id"));
        }

        public String accountId() {
            return Optional.ofNullable(getString("pubid"))
                    .orElseGet(() -> getString("account"));
        }

        public Long tmax() {
            try {
                final String value = getString("tmax");
                return StringUtils.isNotBlank(value) ? Long.parseLong(value) : null;
            } catch (NumberFormatException e) {
                throw new InvalidRequestException(e.getMessage());
            }
        }

        public int debug() {
            return Optional.ofNullable(getInteger("debug")).orElse(0);
        }

        public String outputFormat() {
            return getString("of");
        }

        public String outputModule() {
            return getString("om");
        }

        public List<String> requestProfiles() {
            return getListOfStrings("rprof");
        }

        public List<String> impProfiles() {
            return getListOfStrings("iprof");
        }

        public String storedAuctionResponseId() {
            return getString("sarid");
        }

        public List<String> mimes() {
            return getListOfStrings("mimes");
        }

        public Integer w() {
            return Optional.ofNullable(getInteger("ow"))
                    .orElseGet(() -> getInteger("w"));
        }

        public Integer h() {
            return Optional.ofNullable(getInteger("oh"))
                    .orElseGet(() -> getInteger("h"));
        }

        public List<Format> format() {
            final List<Format> formats = new ArrayList<>();
            final Integer w = w();
            final Integer h = h();

            if (w != null && h != null) {
                formats.add(Format.builder().w(w).h(h).build());
            }

            final String sizesAsString = Optional.ofNullable(getString("sizes"))
                    .orElseGet(() -> getString("ms"));

            if (StringUtils.isNotBlank(sizesAsString)) {
                Arrays.stream(sizesAsString.split(","))
                        .map(sizeAsString -> sizeAsString.split("x"))
                        .filter(size -> size.length == 2)
                        .forEach(size -> formats.add(Format.builder()
                                .w(toInt(size[0]))
                                .h(toInt(size[1]))
                                .build()));
            }

            return formats.isEmpty() ? null : formats;
        }

        public String tagId() {
            return getString("slot");
        }

        public Integer minDuration() {
            return getInteger("mindur");
        }

        public Integer maxDuration() {
            return getInteger("maxdur");
        }

        public List<Integer> api() {
            return getListOfIntegers("api");
        }

        public List<Integer> bAttr() {
            return getListOfIntegers("battr");
        }

        public List<Integer> delivery() {
            return getListOfIntegers("delivery");
        }

        public Integer linearity() {
            return getInteger("linearity");
        }

        public Integer minBitrate() {
            return getInteger("minbr");
        }

        public Integer maxBitrate() {
            return getInteger("maxbr");
        }

        public Integer maxExtended() {
            return getInteger("maxex");
        }

        public Integer maxSeq() {
            return getInteger("maxseq");
        }

        public BigDecimal minCpmPerSec() {
            try {
                final String value = getString("mincpms");
                return StringUtils.isNotBlank(value) ? new BigDecimal(value) : null;
            } catch (NumberFormatException e) {
                throw new InvalidRequestException(e.getMessage());
            }
        }

        public Integer podDur() {
            return getInteger("poddur");
        }

        public Integer podId() {
            return getInteger("podid");
        }

        public Integer podSeq() {
            return getInteger("podseq");
        }

        public List<Integer> protocols() {
            return getListOfIntegers("proto");
        }

        public List<Integer> rqdDurs() {
            return getListOfIntegers("rqddurs");
        }

        public Integer sequence() {
            return getInteger("seq");
        }

        public Integer slotInPod() {
            return getInteger("slotinpod");
        }

        public Integer startDelay() {
            return getInteger("startdelay");
        }

        public Integer skip() {
            return getInteger("skip");
        }

        public Integer skipAfter() {
            return getInteger("skipafter");
        }

        public Integer skipMin() {
            return getInteger("skipmin");
        }

        public Integer pos() {
            return getInteger("pos");
        }

        public Integer stitched() {
            return getInteger("stitched");
        }

        public Integer feed() {
            return getInteger("feed");
        }

        public Integer nvol() {
            return getInteger("nvol");
        }

        public Integer placement() {
            return getInteger("placement");
        }

        public Integer plcmt() {
            return getInteger("plcmt");
        }

        public Integer playbackEnd() {
            return getInteger("playbackend");
        }

        public List<Integer> playbackMethod() {
            return getListOfIntegers("playbackmethod");
        }

        public Integer boxingAllowed() {
            return getInteger("boxingallowed");
        }

        public List<Integer> bType() {
            return getListOfIntegers("btype");
        }

        public List<Integer> expDir() {
            return getListOfIntegers("expdir");
        }

        public Integer topFrame() {
            return getInteger("topframe");
        }

        public Integer targeting() {
            return null; // TODO: GET
        }

        public String consent() { // TODO: GET
            return Optional.ofNullable(getString("consent"))
                    .or(() -> Optional.ofNullable(getString("gdpr_consent")))
                    .orElseGet(() -> getString("consent_string"));
        }

        public Integer gdpr() {
            return Optional.ofNullable(getInteger("gdpr"))
                    .or(() -> Optional.ofNullable(getInteger("privacy")))
                    .orElseGet(() -> getInteger("gdpr_applies"));
        }

        public String usPrivacy() {
            return getString("usp");
        }

        public String consentedProviders() {
            return getString("addtl_consent");
        }

        public Integer consentType() {
            return getInteger("consent_type");
        }

        public List<Integer> gppSid() {
            return getListOfIntegers("gpp_sid");
        }

        public Integer coppa() {
            return getInteger("coppa");
        }

        public String gpc() {
            return Optional.ofNullable(getString("gpc"))
                    .orElseGet(() -> paramsExtractor.gpcFrom(httpRequestContext));
        }

        public Integer dnt() {
            return getInteger("dnt");
        }

        public Integer lmt() {
            return getInteger("lmt");
        }

        public List<String> bCat() {
            return getListOfStrings("bcat");
        }

        public List<String> bAdv() {
            return getListOfStrings("badv");
        }

        public String page() {
            return getString("page");
        }

        public String bundle() {
            return getString("bundle");
        }

        public String name() {
            return getString("name");
        }

        public String storeUrl() {
            return getString("storeurl");
        }

        public String genre() {
            return getString("cgenre");
        }

        public String language() {
            return getString("clang");
        }

        public String contentRating() {
            return getString("crating");
        }

        public List<String> cat() {
            return getListOfStrings("ccat");
        }

        public Integer catTax() {
            return getInteger("ccattax");
        }

        public String series() {
            return Optional.ofNullable(getString("cseries"))
                    .orElseGet(() -> getString("rss_feed"));
        }

        public String title() {
            return getString("ctitle");
        }

        public String url() {
            return getString("curl");
        }

        public Integer liveStream() {
            return getInteger("clivestream");
        }

        public IpAddress ip() {
            return Optional.ofNullable(getString("ip"))
                    .map(ipAddressHelper::toIpAddress)
                    .orElse(null);
        }

        public String ua() {
            return getString("ua");
        }

        public Integer deviceType() {
            return getInteger("dtype");
        }

        public String ifa() {
            return getString("ifa");
        }

        public String ifaType() {
            return getString("ifat");
        }

        private String getString(String key) {
            return httpRequestContext.getQueryParams().get(key);
        }

        private Integer getInteger(String key) {
            return toInt(getString(key));
        }

        private static Integer toInt(String value) {
            try {
                return StringUtils.isNotBlank(value) ? Integer.parseInt(value) : null;
            } catch (NumberFormatException e) {
                throw new InvalidRequestException(e.getMessage());
            }
        }

        private List<String> getListOfStrings(String key) {
            final String value = getString(key);
            if (StringUtils.isBlank(value)) {
                return null;
            }

            return Arrays.asList(value.split(","));
        }

        private List<Integer> getListOfIntegers(String key) {
            final List<String> listOfStrings = getListOfStrings(key);
            return listOfStrings != null
                    ? listOfStrings.stream()
                    .map(GetInterfaceParams::toInt)
                    .toList()
                    : null;
        }
    }
}
