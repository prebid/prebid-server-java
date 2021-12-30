package org.prebid.server.auction.model;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class BidderMessageFactory {

    private static final Set<String> BIDDER_TAG = Set.of("BIDDER");

    private BidderMessageFactory() {
    }

    public static PrebidMessage error(BidderMessageType type, String message) {
        return PrebidMessage.of(makeTags(type.getTag(), "ERROR"), message);
    }

    public static PrebidMessage warning(BidderMessageType type, String message) {
        return PrebidMessage.of(makeTags(type.getTag(), "WARNING"), message);
    }

    public static PrebidMessage debug(BidderMessageType type, String message) {
        return PrebidMessage.of(makeTags(type.getTag(), "DEBUG"), message);
    }

    private static Set<String> makeTags(String... newTags) {
        final HashSet<String> resultingTags = new HashSet<>(BIDDER_TAG);
        GenericMessageFactory.addGeneric(resultingTags);
        resultingTags.addAll(Arrays.asList(newTags));

        return resultingTags;
    }
}
