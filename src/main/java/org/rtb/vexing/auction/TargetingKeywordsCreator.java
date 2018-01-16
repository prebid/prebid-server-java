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

public class TargetingKeywordsCreator {

    private static final Logger logger = LoggerFactory.getLogger(TargetingKeywordsCreator.class);

    private static final String HB_PB_KEY = "hb_pb";
    private static final String HB_BIDDER_KEY = "hb_bidder";
    private static final String HB_CACHE_ID_KEY = "hb_cache_id";
    private static final String HB_SIZE_KEY = "hb_size";
    private static final String HB_DEAL_KEY = "hb_deal";
    private static final String HB_CREATIVE_LOADTYPE_KEY = "hb_creative_loadtype";
    private static final String HB_CREATIVE_LOADTYPE_DEMAND_SDK_VALUE = "demand_sdk";
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

    public static TargetingKeywordsCreator withSettings(String priceGranularity, Integer maxKeyLength) {
        return new TargetingKeywordsCreator(
                priceGranularity,
                parsePriceGranularity(priceGranularity),
                maxKeyLength != null && maxKeyLength != 0 ? maxKeyLength : Integer.MAX_VALUE);
    }

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

    public boolean isPriceGranularityValid() {
        return priceGranularity != null;
    }

    public String priceGranularity() {
        return priceGranularityString;
    }

    public Map<String, String> makeFor(Bid bid, boolean winningBid) {
        return makeFor(bid.bidder, winningBid, bid.price, StringUtils.EMPTY, bid.width, bid.height, bid.cacheId,
                bid.dealId);
    }

    public Map<String, String> makeFor(com.iab.openrtb.response.Bid bid, String bidder, boolean winningBid) {
        return makeFor(bidder, winningBid,
                bid.getPrice(), "0.0", bid.getW(), bid.getH(), StringUtils.EMPTY, bid.getDealid());
    }

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

    private static String sizeFrom(Integer width, Integer height) {
        return width != null && width != 0 && height != null && height != 0
                ? String.format("%sx%s", width, height)
                : null;
    }

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
