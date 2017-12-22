package org.rtb.vexing.auction;

import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.settings.model.Account;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TargetingKeywords {

    private static final String HB_PB_KEY = "hb_pb";
    private static final String HB_BIDDER_KEY = "hb_bidder";
    private static final String HB_CACHE_ID_KEY = "hb_cache_id";
    private static final String HB_SIZE_KEY = "hb_size";
    private static final String HB_DEAL_KEY = "hb_deal";
    private static final String HB_CREATIVE_LOADTYPE_KEY = "hb_creative_loadtype";
    private static final String HB_CREATIVE_LOADTYPE_DEMAND_SDK_VALUE = "demand_sdk";
    private static final String HB_CREATIVE_LOADTYPE_HTML_VALUE = "html";

    private TargetingKeywords() {
    }

    public static List<Bid> addTargetingKeywords(PreBidRequest preBidRequest, List<Bid> bids, Account account) {
        Objects.requireNonNull(preBidRequest);
        Objects.requireNonNull(bids);
        Objects.requireNonNull(account);

        final List<Bid> bidsWithKeywords = new ArrayList<>(bids.size());

        final String priceGranularity = StringUtils.defaultIfBlank(account.priceGranularity, "med");

        for (int i = 0; i < bids.size(); i++) {
            final Bid bid = bids.get(i);
            final boolean topBid = i == 0;

            final String roundedCpm = CpmBucket.fromCpm(bid.price, priceGranularity);
            final String hbSize = sizeFrom(bid);

            final Keywords keywords = new Keywords(preBidRequest, bid, topBid);
            keywords.put(HB_PB_KEY, roundedCpm);
            keywords.put(HB_BIDDER_KEY, bid.bidder);
            keywords.put(HB_CACHE_ID_KEY, bid.cacheId);
            if (hbSize != null) {
                keywords.put(HB_SIZE_KEY, hbSize);
            }
            if (StringUtils.isNotBlank(bid.dealId)) {
                keywords.put(HB_DEAL_KEY, bid.dealId);
            }

            // For the top bid, we want to put the following additional key
            if (topBid) {
                keywords.putDirect(HB_CREATIVE_LOADTYPE_KEY,
                        Objects.equals(bid.bidder, "audienceNetwork")
                                ? HB_CREATIVE_LOADTYPE_DEMAND_SDK_VALUE : HB_CREATIVE_LOADTYPE_HTML_VALUE);
            }

            bidsWithKeywords.add(bid.toBuilder().adServerTargeting(keywords.keywords).build());
        }

        return bidsWithKeywords;
    }

    private static String sizeFrom(Bid bid) {
        return bid.width != null && bid.width != 0
                && bid.height != null && bid.height != 0
                ? String.format("%sx%s", bid.width, bid.height)
                : null;
    }

    private static class Keywords {
        final String bidder;
        final int maxKeyLength;
        final boolean topBid;
        final Map<String, String> keywords;

        Keywords(PreBidRequest preBidRequest, Bid bid, boolean topBid) {
            this.maxKeyLength = preBidRequest.maxKeyLength != null && preBidRequest.maxKeyLength != 0
                    ? preBidRequest.maxKeyLength : Integer.MAX_VALUE;
            this.bidder = bid.bidder;
            this.topBid = topBid;
            this.keywords = bid.adServerTargeting != null ? new HashMap<>(bid.adServerTargeting) : new HashMap<>();
        }

        void put(String prefix, String value) {
            createKeys(prefix).forEach(key -> keywords.put(key, value));
        }

        void putDirect(String key, String value) {
            keywords.put(key, value);
        }

        private List<String> createKeys(String prefix) {
            final List<String> keys = new ArrayList<>(2);
            keys.add(StringUtils.truncate(String.format("%s_%s", prefix, bidder), maxKeyLength));
            // For the top bid, we want to put additional keys apart from bidder-suffixed
            if (topBid) {
                keys.add(prefix);
            }
            return keys;
        }
    }
}
