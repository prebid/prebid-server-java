package org.prebid.server.analytics.reporter.greenbids;

import com.iab.openrtb.response.Bid;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.reporter.greenbids.model.AnalyticsOptions;
import org.prebid.server.analytics.reporter.greenbids.model.AuctionCacheManager;
import org.prebid.server.analytics.reporter.greenbids.model.CachedAuction;
import org.prebid.server.analytics.reporter.greenbids.model.CommonMessage;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsAnalyticsProperties;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsBidder;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.vertx.Initializable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

public class GreenbidsAnalyticsReporter implements AnalyticsReporter, Initializable {

    private static final Logger logger = LoggerFactory.getLogger(GreenbidsAnalyticsReporter.class.getName());
    private static final Random random = new Random();
    private AnalyticsOptions analyticsOptions = new AnalyticsOptions(); // TODO set event auction
    private static final String ANALYTICS_SERVER = "https://a.greenbids.ai";

    public String pbuid;
    public Double greenbidsSampling;
    public Double exploratorySamplingSplit;

    //private final Map<EventType, GreenbidsEventHandler> eventHandlers;
    //private GreenbidsConfig greenbidsConfig;

    //private static final String  CONFIG_URL_SUFFIX = "/bootstrap?scopeId=";
    //private final long configurationRefreshDelay;
    //private final long timeout;
    //private final HttpClient httpClient;
    //private final JacksonMapper jacksonMapper;
    //private final Vertx vertx;



    // constructor
    public GreenbidsAnalyticsReporter(
            GreenbidsAnalyticsProperties greenbidsAnalyticsProperties,
            //HttpClient httpClient,
            JacksonMapper jacksonMapper,
            Vertx vertx
    ) {
        //this.eventHandlers = createEventHandlers(greenbidsAnalyticsProperties, httpClient, jacksonMapper);

        this.pbuid = Objects.requireNonNull(greenbidsAnalyticsProperties.getPbuid());
        this.greenbidsSampling = greenbidsAnalyticsProperties.getGreenbidsSampling();
        this.exploratorySamplingSplit = greenbidsAnalyticsProperties.getExploratorySamplingSplit();
    }


    public <T> CommonMessage createCommonMessage(String auctiondId, T event) throws Exception {
        AuctionCacheManager auctionCacheManager = new AuctionCacheManager();
        CachedAuction cachedAuction = auctionCacheManager.getCachedAuction(auctiondId);

        return new CommonMessage(
                auctiondId,
                getAuctionEvent(event).orElseThrow(() -> new Exception("AuctionEvent not found")),
                greenbidsSampling,
                cachedAuction
        );
    }

    public <T> GreenbidsBidder serializeBidResponse(Bid bid, BidderStatus bidderStatus, T event) {
        AuctionEvent auctuionEvent = getAuctionEvent(bidderStatus).get();

        Integer notBiddingReason = auctuionEvent.getBidResponse().getNbr();

        return new GreenbidsBidder(
                bid.getBidder(),
                status == auctionEvent,
                status == bidderStatus.hasBid()
        );

        /*
        return Map.of(
                "bidder", bid.getBidder(),
                "isTimeout", auctuionEvent.,
                "hasBid", bidderStatus.hasBid()
        );
         */
    }


    // Attempts to extract an AuctionEvent from a generic event object.
    public <T> Optional<AuctionEvent> getAuctionEvent(T event) {
        if (event instanceof AuctionEvent auctionEvent) {
            return Optional.of(auctionEvent);
        } else {
            return Optional.empty();
        }
    }


    // sample auction by computing the hash from greenbidsId
    public static boolean isSampled(
            String greenbidsId,
            double samplingRate,
            double exploratorySamplingSplit
    ) {
        // Check if the sampling rate is within the valid range (0-1)
        if (samplingRate < 0 || samplingRate > 1) {
            System.out.println("Warning: Sampling rate must be between 0 and 1");
            return true;
        }

        // Calculate the exploratory and throttled sampling rates based on the sampling rate and exploratory sampling split
        double exploratorySamplingRate = samplingRate * exploratorySamplingSplit;
        double throttledSamplingRate  = samplingRate * (1.0 - exploratorySamplingSplit);

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


    /*
    @Override
    public <T> Future<Void> processEvent(T event) { // ??? vertx.Future
        final EventType eventType; // ??? final

        if (event instanceof AmpEvent) {
            eventType = EventType.amp;
        } else if (event instanceof AuctionEvent) {
            eventType = EventType.auction;
        } else if (event instanceof CookieSyncEvent) {
            eventType = EventType.cookiesync;
        } else if (event instanceof NotificationEvent) {
            eventType = EventType.notification;
        } else if (event instanceof SetuidEvent) {
            eventType = EventType.setuid;
        } else if (event instanceof VideoEvent) {
            eventType = EventType.video;
        } else {
            eventType = null
        }

        if (eventType != null) {
            eventHandlers.get(eventType).handle(event);
        }

        return Future.succeededFuture();
    }

    // createEventHandlers used in constructor
    private static Map<EventType, GreenbidsEventHandler> createEventHandlers(
            GreenbidsAnalyticsProperties greenbidsAnalyticsProperties,
            HttpClient httpClient,
            JacksonMapper jacksonMapper,
            Vertx vertx
    ) {
        return Arrays.stream(EventType.values())
                .collect(
                        Collectors.toMap(
                                Function.identity(),
                                eventType -> new GreenbidsEventHandler(
                                        greenbidsAnalyticsProperties,
                                        false,
                                        buildEventEndpointUrl(greenbidsAnalyticsProperties.getEndpoint(), eventType),
                                        jacksonMapper,
                                        httpClient,
                                        vertx
                                )
                        )
                );
    }

    private static String buildEventEndpointUrl(String endpoint, EventType eventType) {
        return HttpUtil.validateUrl(endpoint + EVENT_REPORT_ENDPOINT_PATH + eventType.name());
    }
     */
}
