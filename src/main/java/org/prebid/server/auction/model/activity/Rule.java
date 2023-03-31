package org.prebid.server.auction.model.activity;

public interface Rule {

    boolean matches(Object value);

    boolean allowed();
}
