package org.prebid.server.auction.model;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PrivacyMessageFactory {

    private static final Set<String> PRIVACY_TAG = Set.of("PRIVACY");

    private PrivacyMessageFactory() {
    }

    public static PrebidMessage error(PrivacyMessageType type, String message) {
        return PrebidMessage.of(makeTags(type.getTag(), "ERROR"), message);
    }

    public static PrebidMessage warning(PrivacyMessageType type, String message) {
        return PrebidMessage.of(makeTags(type.getTag(), "WARNING"), message);
    }

    public static PrebidMessage debug(PrivacyMessageType type, String message) {
        return PrebidMessage.of(makeTags(type.getTag(), "DEBUG"), message);
    }

    private static Set<String> makeTags(String... newTags) {
        final HashSet<String> resultingTags = new HashSet<>(PRIVACY_TAG);
        GenericMessageFactory.addGeneric(resultingTags);
        resultingTags.addAll(Arrays.asList(newTags));

        return resultingTags;
    }
}
