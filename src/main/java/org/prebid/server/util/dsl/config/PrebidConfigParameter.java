package org.prebid.server.util.dsl.config;

public sealed interface PrebidConfigParameter {

    non-sealed interface Direct extends PrebidConfigParameter {

        Iterable<String> values();
    }

    sealed interface Indirect extends PrebidConfigParameter {

        record Wildcard() implements Indirect {

            private static final Wildcard INSTANCE = new Wildcard();
        }
    }

    static Indirect.Wildcard wildcard() {
        return Indirect.Wildcard.INSTANCE;
    }
}
