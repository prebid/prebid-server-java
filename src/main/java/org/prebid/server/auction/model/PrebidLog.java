package org.prebid.server.auction.model;

import lombok.Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Value(staticConstructor = "of")
public class PrebidLog {

    List<PrebidMessage> warnings = new ArrayList<>();

    List<PrebidMessage> errors = new ArrayList<>();

    public static PrebidLog empty() {
        return PrebidLog.of();
    }

    public void addError(PrebidMessage prebidMessage) {
        if (prebidMessage != null) {
            errors.add(prebidMessage);
        }
    }

    public void addWarning(PrebidMessage prebidMessage) {
        if (prebidMessage != null) {
            warnings.add(prebidMessage);
        }
    }

    public void merge(PrebidLog prebidLog) {
        if (prebidLog != null) {
            errors.addAll(prebidLog.getErrors());
            warnings.addAll(prebidLog.getWarnings());
        }
    }

    public List<String> getIncorrectFirstPartyMessages() {
        return Stream.of(errors, warnings)
                .flatMap(Collection::stream)
                .map(PrebidMessage::getMessage)
                .filter(message -> message.startsWith("Incorrect type for first party")
                        || message.contains("field ignored. Expected"))
                .collect(Collectors.toList());
    }
}
