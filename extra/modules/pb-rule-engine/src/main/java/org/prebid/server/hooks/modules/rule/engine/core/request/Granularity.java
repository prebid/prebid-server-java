package org.prebid.server.hooks.modules.rule.engine.core.request;

public sealed interface Granularity {

    final class Request implements Granularity {
        private static final Request INSTANCE = new Request();

        private Request() {
        }

        public static Request instance() {
            return INSTANCE;
        }
    }

    record Imp(String impId) implements Granularity {
    }
}
