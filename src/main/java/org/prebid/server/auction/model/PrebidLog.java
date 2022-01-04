package org.prebid.server.auction.model;

import lombok.Value;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Value(staticConstructor = "of")
public class PrebidLog {

    Map<String, Set<PrebidMessage>> log = new HashMap<>();

    public void logMessage(PrebidMessage prebidMessage) {
        prebidMessage.getTags().forEach(
                tag -> log.computeIfAbsent(tag, type -> new HashSet<>()).add(prebidMessage));
    }

    public Set<PrebidMessage> getPrebidMessagesByTag(String tag) {
        final Set<PrebidMessage> prebidMessages = log.get(tag);
        return prebidMessages == null ? Collections.emptySet() : prebidMessages;
    }

    public Set<PrebidMessage> getPrebidMessagesByTags(List<String> tags) {
        Set<PrebidMessage> result = new HashSet<>();
        tags.stream()
                .map(log::get)
                .filter(Objects::nonNull)
                .forEach(result::addAll);
        return result;
    }

    public void mergeOtherLog(PrebidLog prebidLog) {
        if (prebidLog != null) {
            log.putAll(prebidLog.getLog());
        }
    }
}
