package org.prebid.server.auction.model;

public sealed interface RejectionResult permits RejectionResult.Allowed, RejectionResult.Rejected {

    static Allowed allowed() {
        return Allowed.INSTANCE;
    }

    static Rejected rejected(Integer nbr) {
        return new Rejected(nbr);
    }

    final class Allowed implements RejectionResult {

        private static final Allowed INSTANCE = new Allowed();
    }

    record Rejected(Integer nbr) implements RejectionResult {
    }
}
