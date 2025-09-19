package org.prebid.server.hooks.modules.rule.engine.core.request.result.functions.filter;

import com.iab.openrtb.request.Imp;

import java.util.Set;

public sealed interface FilterBiddersResult {

    record NoAction() implements FilterBiddersResult {
        private static final NoAction INSTANCE = new NoAction();

        public static NoAction instance() {
            return INSTANCE;
        }
    }

    record Update(Imp imp, Set<String> bidders) implements FilterBiddersResult {
    }

    record Reject(Set<String> bidders) implements FilterBiddersResult {
    }
}
