package org.prebid.server.deals.model;

import org.prebid.server.proto.openrtb.ext.response.ExtTraceDeal;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceDeal.Category;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class DeepDebugLog {

    private final boolean deepDebugEnabled;

    private final List<ExtTraceDeal> entries;

    private final Clock clock;

    private DeepDebugLog(boolean deepDebugEnabled, Clock clock) {
        this.deepDebugEnabled = deepDebugEnabled;
        this.entries = deepDebugEnabled ? new LinkedList<>() : null;
        this.clock = Objects.requireNonNull(clock);
    }

    public static DeepDebugLog create(boolean deepDebugEnabled, Clock clock) {
        return new DeepDebugLog(deepDebugEnabled, clock);
    }

    public void add(String lineItemId, Category category, Supplier<String> messageSupplier) {
        if (deepDebugEnabled) {
            entries.add(ExtTraceDeal.of(lineItemId, ZonedDateTime.now(clock), category, messageSupplier.get()));
        }
    }

    public boolean isDeepDebugEnabled() {
        return deepDebugEnabled;
    }

    public List<ExtTraceDeal> entries() {
        return entries == null ? Collections.emptyList() : Collections.unmodifiableList(entries);
    }
}
