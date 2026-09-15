package org.prebid.server.hooks.modules.id5.userid.v1.model;

import com.iab.openrtb.request.Eid;

import java.util.List;

public record Id5UserId(List<Eid> eids) {

    private static final Id5UserId EMPTY = new Id5UserId(List.of());

    public static Id5UserId empty() {
        return EMPTY;
    }
}
