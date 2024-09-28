package org.prebid.server.auction;

import com.iab.openrtb.response.Bid;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * Exists to support the Prebid Universal Creative. If it exists, the only legal value is mobile-app.
     * It will exist only if the incoming bidRequest defiend request.app instead of request.site.
     */
    private static final String ENV_KEY = "_env";
    /**
     * Name of the Bidder. For example, "appnexus" or "rubicon".
     */
    private static final String BIDDER_KEY = "_bidder";
    /**
     * Respects rounded CPM value.
     */
    private static final String PB_KEY = "_pb";
    /**
     * Describes the size in format: [Width]x[Height].
     */
    private static final String SIZE_KEY = "_size";
    /**
     * Stores the UUID which can be used to fetch the bid data from prebid cache.
     * Callers should *never* assume that this exists, since the call to the cache may always fail.
     */
    private static final String CACHE_ID_KEY = "_cache_id";
    /**
     * Stores the UUID which can be used to fetch the video XML data from prebid cache.
     * Callers should *never* assume that this exists, since the call to the cache may always fail.
     */
    private static final String VAST_ID_KEY = "_uuid";
    /**
     * Stores the deal ID for the given bid.
     */
    private static final String DEAL_KEY = "_deal";
    /**
     * Stores protocol, host and port for cache service endpoint.
     */
    private static final String CACHE_HOST_KEY = "_cache_host";
    /**
     * Stores http path for cache service endpoint.
     */
    private static final String CACHE_PATH_KEY = "_cache_path";
    /**
     * Stores category duration for video bids
     */
    private static final String CATEGORY_DURATION_KEY = "_pb_cat_dur";

    /**
     * Stores bid's format. For example "video" or "banner".
     */
    private static final String FORMAT_KEY = "_format";

    private static final String DEFAULT_CPM = "0.0";

    private final PriceGranularity priceGranularity;
    private final boolean includeWinners;
    private final boolean includeBidderKeys;
    private final boolean alwaysIncludeDeals;
    private final boolean includeFormat;
    private final String env;
    private final int truncateAttrChars;
    private final String cacheHost;
    private final String cachePath;
    private final TargetingKeywordsResolver resolver;
    private final String keyPrefix;

    private TargetingKeywordsCreator(PriceGranularity priceGranularity,
                                     boolean includeWinners,
                                     boolean includeBidderKeys,
                                     boolean alwaysIncludeDeals,
                                     boolean includeFormat,
                                     String env,
                                     int truncateAttrChars,
                                     String cacheHost,
                                     String cachePath,
                                     TargetingKeywordsResolver resolver,
                                     String keyPrefix) {

        this.priceGranularity = priceGranularity;
        this.includeWinners = includeWinners;
        this.includeBidderKeys = includeBidderKeys;
        this.alwaysIncludeDeals = alwaysIncludeDeals;
        this.includeFormat = includeFormat;
        this.env = env;
        this.truncateAttrChars = truncateAttrChars;
        this.cacheHost = cacheHost;
        this.cachePath = cachePath;
        this.resolver = resolver;
        this.keyPrefix = keyPrefix;
    }

    /**
     * Creates {@link TargetingKeywordsCreator} for the given params.
     */
    public static TargetingKeywordsCreator create(ExtPriceGranularity extPriceGranularity,
                                                  boolean includeWinners,
                                                  boolean includeBidderKeys,
                                                  boolean alwaysIncludeDeals,
                                                  boolean includeFormat,
                                                  String env,
                                                  int truncateAttrChars,
                                                  String cacheHost,
                                                  String cachePath,
                                                  TargetingKeywordsResolver resolver,
                                                  String keyPrefix) {
        return new TargetingKeywordsCreator(
                PriceGranularity.createFromExtPriceGranularity(extPriceGranularity),
                includeWinners,
                includeBidderKeys,
                alwaysIncludeDeals,
                includeFormat,
                env,
                truncateAttrChars,
                cacheHost,
                cachePath,
                resolver,
                keyPrefix);
    }

    /**
     * Creates map of keywords for the given {@link Bid}.
     */
    Map<String, String> makeFor(Bid bid,
                                String bidder,
                                boolean winningBid,
                                String cacheId,
                                String format,
                                String vastCacheId,
                                String categoryDuration) {

        final Map<String, String> keywords = makeFor(
                bidder,
                winningBid,
                bid.getPrice(),
                bid.getW(),
                bid.getH(),
                cacheId,
                vastCacheId,
                categoryDuration,
                format,
                bid.getDealid());

        if (resolver == null) {
            return truncateKeys(keywords);
        }

        final Map<String, String> augmentedKeywords = new HashMap<>(keywords);
        augmentedKeywords.putAll(resolver.resolve(bid, bidder));

        return truncateKeys(augmentedKeywords);
    }

    /**
     * Common method for creating targeting keywords.
     */
    private Map<String, String> makeFor(String bidder,
                                        boolean winningBid,
                                        BigDecimal price,
                                        Integer width,
                                        Integer height,
                                        String cacheId,
                                        String vastCacheId,
                                        String categoryDuration,
                                        String format,
                                        String dealId) {

        final boolean includeDealBid = alwaysIncludeDeals && StringUtils.isNotEmpty(dealId);
        final KeywordMap keywordMap = new KeywordMap(
                bidder,
                winningBid,
                includeWinners,
                includeBidderKeys || includeDealBid,
                Collections.emptySet());

        final String roundedCpm = isPriceGranularityValid() ? CpmRange.fromCpm(price, priceGranularity) : DEFAULT_CPM;
        keywordMap.put(this.keyPrefix + PB_KEY, roundedCpm);

        keywordMap.put(this.keyPrefix + BIDDER_KEY, bidder);

        final String hbSize = sizeFrom(width, height);
        if (hbSize != null) {
            keywordMap.put(this.keyPrefix + SIZE_KEY, hbSize);
        }
        if (StringUtils.isNotBlank(cacheId)) {
            keywordMap.put(this.keyPrefix + CACHE_ID_KEY, cacheId);
        }
        if (StringUtils.isNotBlank(vastCacheId)) {
            keywordMap.put(this.keyPrefix + VAST_ID_KEY, vastCacheId);
        }
        if ((StringUtils.isNotBlank(vastCacheId) || StringUtils.isNotBlank(cacheId))
                && cacheHost != null && cachePath != null) {
            keywordMap.put(this.keyPrefix + CACHE_HOST_KEY, cacheHost);
            keywordMap.put(this.keyPrefix + CACHE_PATH_KEY, cachePath);
        }
        if (StringUtils.isNotBlank(format) && includeFormat) {
            keywordMap.put(this.keyPrefix + FORMAT_KEY, format);
        }

        // get Line Item by dealId
        if (StringUtils.isNotBlank(dealId)) {
            keywordMap.put(this.keyPrefix + DEAL_KEY, dealId);
        }
        if (env != null) {
            keywordMap.put(this.keyPrefix + ENV_KEY, env);
        }
        if (StringUtils.isNotBlank(categoryDuration)) {
            keywordMap.put(this.keyPrefix + CATEGORY_DURATION_KEY, categoryDuration);
        }

        return keywordMap.asMap();
    }

    /**
     * Checks price granularity value is defined.
     */
    private boolean isPriceGranularityValid() {
        return priceGranularity != null;
    }

    /**
     * Composes size from width and height params.
     *
     * @return null if at least one parameter is missing or equals to 0.
     */
    private static String sizeFrom(Integer width, Integer height) {
        return width != null && width != 0 && height != null && height != 0
                ? "%sx%s".formatted(width, height)
                : null;
    }

    private Map<String, String> truncateKeys(Map<String, String> keyValues) {
        return truncateAttrChars > 0
                ? keyValues.entrySet().stream()
                .collect(Collectors
                        .toMap(keyValue -> truncateKey(keyValue.getKey()), Map.Entry::getValue, (key1, key2) -> key1))
                : keyValues;
    }

    private String truncateKey(String key) {
        return key.length() > truncateAttrChars
                ? key.substring(0, truncateAttrChars)
                : key;
    }

    /**
     * Helper for targeting keywords.
     * <p>
     * Brings a convenient way for creating keywords regarding to bidder and winning bid flag.
     */
    private static class KeywordMap {

        private final String bidder;
        private final boolean winningBid;
        private final boolean includeWinners;
        private final boolean includeBidderKeys;
        private final Set<String> excludedBidderKeys;

        private final Map<String, String> keywords;

        KeywordMap(String bidder, boolean winningBid, boolean includeWinners, boolean includeBidderKeys,
                   Set<String> excludedBidderKeys) {
            this.bidder = bidder;
            this.winningBid = winningBid;
            this.includeWinners = includeWinners;
            this.includeBidderKeys = includeBidderKeys;
            this.excludedBidderKeys = excludedBidderKeys;

            this.keywords = new HashMap<>();
        }

        void put(String prefix, String value) {
            createKeys(prefix).forEach(key -> keywords.put(key, value));
        }

        private List<String> createKeys(String prefix) {
            final List<String> keys = new ArrayList<>(2);
            if (includeBidderKeys && !excludedBidderKeys.contains(prefix)) {
                keys.add("%s_%s".formatted(prefix, bidder));
            }
            // For the top bid, we want to put additional keys apart from bidder-suffixed
            if (winningBid && includeWinners) {
                keys.add(prefix);
            }
            return keys;
        }

        private Map<String, String> asMap() {
            return keywords;
        }
    }
}
