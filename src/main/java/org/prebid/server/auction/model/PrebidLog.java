package org.prebid.server.auction.model;

import lombok.Value;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Value(staticConstructor = "of")
public class PrebidLog {

    private static final Map<String, Set<PrebidMessage>> log = new HashMap<>();

    public static void logMessage(PrebidMessage prebidMessage) {
        prebidMessage.getTags().forEach(
                tag -> log.computeIfAbsent(tag, type -> new HashSet<>()).add(prebidMessage));
    }

    public static Set<PrebidMessage> getPrebidMessagesByTag(String tag) {
        return log.get(tag);
    }

    public static Set<PrebidMessage> getPrebidMessagesByTags(List<String> tags) {
        Set<PrebidMessage> result = new HashSet<>();
        tags.forEach(tag -> result.addAll(log.get(tag)));
        return result;
    }

}
