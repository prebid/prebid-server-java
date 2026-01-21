package org.prebid.server.hooks.modules.id5.userid.v1.filter;

public record FilterResult(boolean isAccepted, String reason) {
    private static final FilterResult ACCEPTED = new FilterResult(true, "");

    public static FilterResult rejected(String reason) {
        return new FilterResult(false, reason);
    }

    public static FilterResult accepted() {
        return ACCEPTED;
    }
}
