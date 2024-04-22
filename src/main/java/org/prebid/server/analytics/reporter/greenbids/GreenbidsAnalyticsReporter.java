package org.prebid.server.analytics.reporter.greenbids;

import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.model.CookieSyncEvent;
import org.prebid.server.analytics.model.NotificationEvent;
import org.prebid.server.analytics.model.SetuidEvent;
import org.prebid.server.analytics.model.VideoEvent;
import org.prebid.server.analytics.reporter.greenbids.model.AdUnit;
import org.prebid.server.analytics.reporter.greenbids.model.AuctionCacheManager;
import org.prebid.server.analytics.reporter.greenbids.model.CachedAuction;
import org.prebid.server.analytics.reporter.greenbids.model.CommonMessage;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsAnalyticsProperties;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsBidder;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsConfig;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsEvent;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.NonBid;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.SeatNonBid;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.Initializable;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.UUID.randomUUID;

public class GreenbidsAnalyticsReporter implements AnalyticsReporter, Initializable {

    private static final Logger logger = LoggerFactory.getLogger(GreenbidsAnalyticsReporter.class.getName());
    private static final Random random = new Random();
    //private AnalyticsOptions analyticsOptions = new AnalyticsOptions(); // TODO set event auction
    private static final String ANALYTICS_SERVER = "https://a.greenbids.ai";

    public String pbuid;
    public Double greenbidsSampling;
    public Double exploratorySamplingSplit;

    //private final Map<EventType, GreenbidsEventHandler> eventHandlers;
    //private static final String  CONFIG_URL_SUFFIX = "/bootstrap?scopeId=";

    private final long configurationRefreshDelay;
    private final Vertx vertx;
    private GreenbidsConfig greenbidsConfig;
    private final HttpClient httpClient;
    private final long timeout;
    private final JacksonMapper jacksonMapper;

    private CachedAuction cachedAuction;




    // constructor
    public GreenbidsAnalyticsReporter(
            GreenbidsAnalyticsProperties greenbidsAnalyticsProperties,
            HttpClient httpClient,
            JacksonMapper jacksonMapper,
            Vertx vertx
    ) {
        this.pbuid = Objects.requireNonNull(greenbidsAnalyticsProperties.getPbuid());
        this.greenbidsSampling = greenbidsAnalyticsProperties.getGreenbidsSampling();
        this.exploratorySamplingSplit = greenbidsAnalyticsProperties.getExploratorySamplingSplit();
        this.configurationRefreshDelay = Objects.requireNonNull(greenbidsAnalyticsProperties.getConfigurationRefreshDelayMs());
        this.vertx = Objects.requireNonNull(vertx);
        this.greenbidsConfig = GreenbidsConfig.of(
                greenbidsAnalyticsProperties.getPbuid(),
                greenbidsAnalyticsProperties.getGreenbidsSampling(),
                greenbidsAnalyticsProperties.getExploratorySamplingSplit()
        );
        this.httpClient = Objects.requireNonNull(httpClient);
        this.timeout = Objects.requireNonNull(greenbidsAnalyticsProperties.getTimeoutMs());
        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);
        this.cachedAuction = null;
    }


    public CommonMessage createCommonMessage(String auctiondId, AuctionEvent event) {
        return new CommonMessage(
                auctiondId,
                event,
                greenbidsSampling,
                this.cachedAuction
        );
    }

    // TO CHANGE METHOD
    public GreenbidsBidder serializeBidResponse(SetuidEvent  event) {
        return GreenbidsBidder.builder()
                .bidder(event.getBidder())
                .isTimeout(event.getStatus() == 408)
                .hasBid(event.getSuccess())
                .build();
    }

    public CommonMessage addBidResponseToMessage(
            CommonMessage commonMessage,
            List<Imp> imps,
            Map<String, Bid> seatsWithBids,
            Map<String, NonBid> seatsWithNonBids
    ) {
        // Map[ImpId, List[GreenbidsBidder]]
        //String adUnitCode = bidder.getAdUnitCode().toLowerCase();
        //Optional<AdUnit> adUnitOptional = commonMessage.getAdUnits().stream();
        //Map<String, List<GreenbidsBidder>> bidderMap = bids.stream()
        //Map<Imp, List<GreenbidsBidder>> impBidderMap = new HashMap<>();

        // Map[ImpId, Imp]
        Map<String, Imp> impMap = imps.stream()
                .collect(Collectors.toMap(Imp::getId, Function.identity()));

        // Map[Imp, List<GreenbidsBidder>>]
        List<AdUnit> adUnits = new ArrayList<>();

        seatsWithBids.


        // process bids
        for (Bid bid: bids) {
            Imp imp = impMap.get(bid.getImpid());

            if (imp != null) {
                impBidderMap.computeIfAbsent(imp, k -> new ArrayList<>())
                    .add(
                            GreenbidsBidder.builder()
                                    .bidder(bid.getId())
                                    .hasBid(bid != null)
                                    .isTimeout(bid.getStatus() == 408)
                    );
            }

            GreenbidsBidder greenbidsBidder = serializeBidResponse(bid);
            impBidderMap.computeIfAbsent(imp, k -> new ArrayList<>()).add(greenbidsBidder);
        }
    }

    public CommonMessage createBidMessage(
            AuctionEvent auctionEvent,
            NotificationEvent notificationEvent
    ){
        // ??? from which event extract list of adUnits? AuctionId? AuctionEndTimestamp???
        AuctionContext auctionContext = auctionEvent.getAuctionContext();

        // get auctionId
        String auctionId = auctionContext.getBidRequest().getId();

        // get adunits
        List<Imp> imps = auctionContext.getBidRequest().getImp();

        // get auction timestamp
        Long auctionTimestamp = auctionContext.getBidRequest().getExt().getPrebid().getAuctiontimestamp();
        Long auctionElapsed = System.currentTimeMillis() - auctionTimestamp;

        // get bids
        Map<String, Bid> seatsWithBids = auctionEvent.getBidResponse().getSeatbid()
                .stream()
                .filter(seatBid -> !seatBid.getBid().isEmpty())
                .collect(
                        Collectors.toMap(
                                SeatBid::getSeat,
                                seatBid -> seatBid.getBid().get(0),
                                (existing, replacement) -> existing
                        )
                );

        // get timeoutBids + nonBids
        Map<String, NonBid> seatsWithNonBids = auctionContext.getBidResponse().getExt().getSeatnonbid()
                .stream()
                .filter(seatNonBid -> !seatNonBid.getNonBid().isEmpty())
                .collect(
                        Collectors.toMap(
                                SeatNonBid::getSeat,
                                seatNonBid -> seatNonBid.getNonBid().get(0),
                                (existing, replacement) -> existing
                        )
                );

        CommonMessage commonMessage = createCommonMessage(auctionId, auctionEvent);
        CommonMessage commonMessageWithBids = addBidResponseToMessage(
                commonMessage,
                imps,
                seatsWithBids,
                seatsWithNonBids
        );

        return commonMessage;
    }

    // get bidder from event setuidEvent, NotificationEvent, CoolieSyncEvent
    // get timeout status code from HttpResponseStatus  REQUEST_TIMEOUT 408

    @Override
    public <T> Future<Void> processEvent(T event){
        final GreenbidsEvent<?> greenbidsEvent;

        AuctionEvent auctionEventGlobal = AuctionEvent.builder().build();
        NotificationEvent  notificationEventGlobal = NotificationEvent.builder().build();

        if (event instanceof AmpEvent ampEvent) {
            greenbidsEvent =  GreenbidsEvent.of("/openrtb2/amp", ampEvent.getBidResponse());
        } else if (event instanceof AuctionEvent auctionEvent) {
            greenbidsEvent =  GreenbidsEvent.of("/openrtb2/auction", auctionEvent.getBidResponse());
            auctionEventGlobal = auctionEvent;
        } else if (event instanceof CookieSyncEvent cookieSyncEvent) {
            greenbidsEvent = GreenbidsEvent.of("/cookie_sync", cookieSyncEvent.getBidderStatus());
        } else if (event instanceof NotificationEvent notificationEvent) {
            greenbidsEvent = GreenbidsEvent.of("/event", notificationEvent.getType() + notificationEvent.getBidId());
            notificationEventGlobal = notificationEvent;
        } else if (event instanceof SetuidEvent setuidEvent) {
            greenbidsEvent = GreenbidsEvent.of(
                    "/setuid",
                    setuidEvent.getBidder() + ":" + setuidEvent.getUid() + ":" + setuidEvent.getSuccess()
            );
            GreenbidsBidder greenbidsBidder = serializeBidResponse(setuidEvent);
        } else if (event instanceof VideoEvent videoEvent) {
            greenbidsEvent = GreenbidsEvent.of("/openrtb2/video", videoEvent.getBidResponse());
        } else {
            greenbidsEvent = GreenbidsEvent.of("unknown", null);
        }

        // generate greenbidsId
        String greenbidsId = randomUUID().toString();

        AuctionContext auctionContext = auctionEventGlobal.getAuctionContext();
        String auctionId = auctionContext.getBidRequest().getId();
        Boolean isSampled = isSampled(greenbidsConfig.getGreenbidsSampling(), greenbidsId);

        AuctionCacheManager auctionCacheManager = new AuctionCacheManager();
        this.cachedAuction = auctionCacheManager.getCachedAuction(auctionId);
        this.cachedAuction.isSampled = isSampled;
        this.cachedAuction.greenbidsId = greenbidsId;

        CommonMessage commonMessage = createBidMessage(auctionEventGlobal, notificationEventGlobal);

        return Future.succeededFuture();
    }


    // Attempts to extract an AuctionEvent from a generic event object.
    /*
    public <T> Optional<AuctionEvent> getAuctionEvent(T event) {
        if (event instanceof AuctionEvent auctionEvent) {
            return Optional.of(auctionEvent);
        } else {
            return Optional.empty();
        }
    }
     */


    // sample auction by computing the hash from greenbidsId
    public boolean isSampled(
            double samplingRate,
            String greenbidsId
    ) {
        // Check if the sampling rate is within the valid range (0-1)
        if (samplingRate < 0 || samplingRate > 1) {
            System.out.println("Warning: Sampling rate must be between 0 and 1");
            return true;
        }

        // Calculate the exploratory and throttled sampling rates based on the sampling rate and exploratory sampling split
        double exploratorySamplingRate = samplingRate * this.exploratorySamplingSplit;
        double throttledSamplingRate  = samplingRate * (1.0 - this.exploratorySamplingSplit);

        // Calculate the hash of the last 4 characters of the greenbidsId
        long hashInt = Math.abs(greenbidsId.hashCode());
        hashInt = hashInt % 0x10000; // Ensure hashInt is a 16-bit value

        // Determine whether the event should be sampled based on the hash value and the sampling rates
        boolean isPrimarySampled = hashInt < exploratorySamplingRate * 0xFFFF;
        if (isPrimarySampled) {
            return true;
        }
        boolean isExtraSampled = hashInt >= (1 - throttledSamplingRate) * 0xFFFF;
        return isExtraSampled;
    }


    public <T> boolean initConfig(T event) {
        try {
            //AuctionEvent auctionEvent = getAuctionEvent(event).orElse(null);
            //String pbuid = auctionEvent.getAuctionContext().getBidRequest().getSite().getPublisher().getId();

            // validate publisherId
            if (this.pbuid == null || this.pbuid.isEmpty()) {
                logger.error("Error initializing analytics config: pbuid is required.");
                return false;
            }

            // handle deprecated sampling
            if (this.greenbidsSampling == null || this.greenbidsSampling > 1) {
                logger.warn("GreenbidsSampling is not set or >=1, using this analytics module unsampled is discouraged.");
                this.greenbidsSampling = 1d;
            }

            return true;
        } catch (Exception e) {
            logger.error("Error initializing analytics config", e);
            return false;
        }
    }

    @Override
    public int vendorId() {
        return 0;
    }

    @Override
    public String name() {
        return "greenbids";
    }

    @Override
    public void initialize() {
        vertx.setPeriodic(configurationRefreshDelay, id -> fetchRemoteConfig());
        fetchRemoteConfig();
    }

    private void fetchRemoteConfig() {
        logger.info("[greenbids] Updating config: {0}", greenbidsConfig);
        httpClient.get(ANALYTICS_SERVER, timeout)
                .map(this::ProcessRemoteConfigurationResponse)
                .onComplete(this::handleConfigResponse);
    }

    private GreenbidsConfig ProcessRemoteConfigurationResponse(HttpClientResponse response) {
        final int statusCode = response.getStatusCode();
        if (statusCode!= 200) {
            throw new PreBidException("[greenbids] Failed to fetch config, reason: HTTP status code " + statusCode);
        }
        final String body = response.getBody();
        try {
            return jacksonMapper.decodeValue(body, GreenbidsConfig.class);
        } catch (DecodeException e) {
            throw new PreBidException(
                    "[greenbids] Failed to fetch config, reason: failed to parse response: " + body, e);
        }
    }

    private String makeEventHandlerEndpoint() {
        try {
            return HttpUtil.validateUrl(ANALYTICS_SERVER);
        } catch (IllegalArgumentException e) {
            final String message = "[greenbids] Failed to create event report url";
            logger.error(message);
            throw new PreBidException(message);
        }
    }
}
