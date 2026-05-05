package org.prebid.server.hooks.modules.id5.userid.v1.model;

import com.iab.openrtb.request.Eid;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record FetchResponse(Map<String, UserId> ids) implements Id5UserId {

    public record UserId(Eid eid) {
    }

    public List<Eid> toEIDs() {
        return Optional.ofNullable(ids)
                .map(userIds -> userIds.values().stream().map(UserId::eid).toList())
                .orElse(Collections.emptyList());
    }
}
