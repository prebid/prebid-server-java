package org.rtb.vexing.auction;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.model.response.Bid;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Used throughout Prebid to create targeting keys as keys which can be used in an ad server like DFP.
 * Clients set the values we assign on the request to the ad server, where they can be substituted like macros into
 * Creatives.
 * <p>
 * Removing one of these, or changing the semantics of what we store there, will probably break the
 * line item setups for many publishers.
 * <p>
 * These are especially important to Prebid Mobile. It's much more cumbersome for a Mobile App to update code
 * than it is for a website. As a result, they rely heavily on these targeting keys so that any changes can
 * be made on Prebid Server and the Ad Server's line items.
 */
public class TargetingKeywordsCreator {

    private static final Logger logger = LoggerFactory.getLogger(TargetingKeywordsCreator.class);

    /**
     * Respects rounded CPM value
     */
    private static final String HB_PB_KEY = "hb_pb";
    /**
     * Name of the Bidder. For example, "appnexus" or "rubicon".
     */
    private static final String HB_BIDDER_KEY = "hb_bidder";
    /**
     * Stores the UUID which can be used to fetch the bid data from prebid cache.
     * Callers should *never* assume that this exists, since the call to the cache may always fail.
     */
    private static final String HB_CACHE_ID_KEY = "hb_cache_id";
    /**
     * Describes the size in format: [Width]x[Height]
     */
    private static final String HB_SIZE_KEY = "hb_size";
    /**
     * Stores the deal ID for the given bid
     */
    private static final String HB_DEAL_KEY = "hb_deal";
    /**
     * Used exclusively by Prebid Mobile to accomodate Facebook.
     * <p>
     * Facebook requires that ads from their network be loaded using their own SDK.
     * <p>
     * Other demand sources are happy to let Prebid Mobile use a Webview.
     */
    private static final String HB_CREATIVE_LOADTYPE_KEY = "hb_creative_loadtype";
    /**
     * Used as a value for HB_CREATIVE_LOADTYPE_KEY
     */
    private static final String HB_CREATIVE_LOADTYPE_DEMAND_SDK_VALUE = "demand_sdk";
    /**
     * Used as a value for HB_CREATIVE_LOADTYPE_KEY
     */
    private static final String HB_CREATIVE_LOADTYPE_HTML_VALUE = "html";

    private final String priceGranularityString;
    private final CpmBucket.PriceGranularity priceGranularity;
    private final int maxKeyLength;

    private TargetingKeywordsCreator(String priceGranularityString, CpmBucket.PriceGranularity priceGranularity,
                                     int maxKeyLength) {
        this.priceGranularityString = priceGranularityString;
        this.priceGranularity = priceGranularity;
        this.maxKeyLength = maxKeyLength;
    }

    /**
     * Creates {@link TargetingKeywordsCreator} for the given params.
     */
    public static TargetingKeywordsCreator withSettings(String priceGranularity, Integer maxKeyLength) {
        return new TargetingKeywordsCreator(
                priceGranularity,
                parsePriceGranularity(priceGranularity),
                maxKeyLength != null && maxKeyLength != 0 ? maxKeyLength : Integer.MAX_VALUE);
    }

    /**
     * Determines the {@link CpmBucket.PriceGranularity} from an input string value.
     */
    private static CpmBucket.PriceGranularity parsePriceGranularity(String priceGranularity) {
        CpmBucket.PriceGranularity result = null;
        if (StringUtils.isBlank(priceGranularity)) {
            result = CpmBucket.PriceGranularity.med;
        } else {
            try {
                result = CpmBucket.PriceGranularity.valueOf(priceGranularity);
            } catch (IllegalArgumentException e) {
                logger.error("Price bucket granularity error: ''{0}'' is not a recognized granularity",
                        priceGranularity);
            }
        }
        return result;
    }

    /**
     * Checks the price granularity is valid.
     */
    public boolean isPriceGranularityValid() {
        return priceGranularity != null;
    }

    /**
     * Compares given price to computed CPM value according to the price granularity.
     */
    public boolean isNonZeroCpm(BigDecimal price) {
        if (isPriceGranularityValid()) {
            final BigDecimal cpm = CpmBucket.fromCpmAsNumber(price, priceGranularity);
            return cpm != null && cpm.compareTo(BigDecimal.ZERO) != 0;
        }
        return false;
    }

    /**
     * Returns the price granularity as string value.
     */
    public String priceGranularity() {
        return priceGranularityString;
    }

    /**
     * Creates map of keywords for the given {@link org.rtb.vexing.model.response.Bid}.
     */
    public Map<String, String> makeFor(Bid bid, boolean winningBid) {
        return makeFor(bid.bidder, winningBid, bid.price, StringUtils.EMPTY, bid.width, bid.height, bid.cacheId,
                bid.dealId);
    }

    /**
     * Creates map of keywords for the given {@link com.iab.openrtb.response.Bid}.
     */
    public Map<String, String> makeFor(com.iab.openrtb.response.Bid bid, String bidder, boolean winningBid,
                                       String cacheId) {
        return makeFor(bidder, winningBid,
                bid.getPrice(), "0.0", bid.getW(), bid.getH(), cacheId, bid.getDealid());
    }

    /**
     * Common method for creating targeting keywords.
     */
    private Map<String, String> makeFor(String bidder, boolean winningBid, BigDecimal price, String defaultCpm,
                                        Integer width, Integer height, String cacheId, String dealId) {
        final String roundedCpm = isPriceGranularityValid() ? CpmBucket.fromCpm(price, priceGranularity) : defaultCpm;
        final String hbSize = sizeFrom(width, height);

        final KeywordMap keywordMap = new KeywordMap(bidder, winningBid);
        keywordMap.put(HB_PB_KEY, roundedCpm);
        keywordMap.put(HB_BIDDER_KEY, bidder);
        if (hbSize != null) {
            keywordMap.put(HB_SIZE_KEY, hbSize);
        }
        if (StringUtils.isNotBlank(cacheId)) {
            keywordMap.put(HB_CACHE_ID_KEY, cacheId);
        }
        if (StringUtils.isNotBlank(dealId)) {
            keywordMap.put(HB_DEAL_KEY, dealId);
        }

        final Map<String, String> keywords = keywordMap.asMap();

        // For the top bid, we want to put the following additional key
        if (winningBid) {
            keywords.put(HB_CREATIVE_LOADTYPE_KEY,
                    Objects.equals(bidder, "audienceNetwork")
                            ? HB_CREATIVE_LOADTYPE_DEMAND_SDK_VALUE : HB_CREATIVE_LOADTYPE_HTML_VALUE);
        }
        return keywords;
    }

    /**
     * Composes size from width and height params.
     *
     * @return null if at least one parameter is missing or equals to 0.
     */
    private static String sizeFrom(Integer width, Integer height) {
        return width != null && width != 0 && height != null && height != 0
                ? String.format("%sx%s", width, height)
                : null;
    }

    /**
     * Helper for targeting keywords.
     * <p>
     * Brings a convenient way for creating keywords regarding to bidder and winning bid flag.
     */
    private class KeywordMap {
        private final String bidder;
        private final boolean winningBid;
        private final Map<String, String> keywords;

        KeywordMap(String bidder, boolean winningBid) {
            this.bidder = bidder;
            this.winningBid = winningBid;
            this.keywords = new HashMap<>();
        }

        void put(String prefix, String value) {
            createKeys(prefix).forEach(key -> keywords.put(key, value));
        }

        private List<String> createKeys(String prefix) {
            final List<String> keys = new ArrayList<>(2);
            keys.add(StringUtils.truncate(String.format("%s_%s", prefix, bidder), maxKeyLength));
            // For the top bid, we want to put additional keys apart from bidder-suffixed
            if (winningBid) {
                keys.add(prefix);
            }
            return keys;
        }

        Map<String, String> asMap() {
            return keywords;
        }
    }
}
