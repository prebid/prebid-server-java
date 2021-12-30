package org.prebid.server.auction.model;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class UnknownMessageFactory {

    private static final Set<String> UNKNOWN_TAG = Set.of("UNKNOWN");

    private UnknownMessageFactory() {
    }

    public static PrebidMessage error(UnknownMessageType type, String message) {
        return PrebidMessage.of(makeTags(type.getTag(), "ERROR"), message);
    }

    public static PrebidMessage warning(UnknownMessageType type, String message) {
        return PrebidMessage.of(makeTags(type.getTag(), "WARNING"), message);
    }

    public static PrebidMessage debug(UnknownMessageType type, String message) {
        return PrebidMessage.of(makeTags(type.getTag(), "DEBUG"), message);
    }

    private static Set<String> makeTags(String... newTags) {
        final HashSet<String> resultingTags = new HashSet<>(UNKNOWN_TAG);
        GenericMessageFactory.addGeneric(resultingTags);
        resultingTags.addAll(Arrays.asList(newTags));

        return resultingTags;
    }
}
