package org.prebid.server.hooks.modules.id5.userid.v1.model;

import com.iab.openrtb.request.Eid;

import java.util.List;

public interface Id5UserId {

    List<Eid> toEIDs();

    Id5UserId EMPTY = List::of;

    static Id5UserId empty() {
        return EMPTY;
    }

}
