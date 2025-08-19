package org.prebid.server.hooks.modules.rule.engine.core.request.result.functions.filter;

import com.iab.openrtb.request.Imp;
import lombok.experimental.Accessors;

import java.util.Set;


public sealed interface FilterBiddersResult {

    @Accessors(fluent = true)
    record NoAction() implements FilterBiddersResult {
        private static final NoAction INSTANCE = new NoAction();

        public static NoAction instance() {
            return INSTANCE;
        }
    }

    @Accessors(fluent = true)
    record Update(Imp imp, Set<String> bidders) implements FilterBiddersResult {
    }

    @Accessors(fluent = true)
    record Reject(Set<String> bidders) implements FilterBiddersResult {
    }
}
