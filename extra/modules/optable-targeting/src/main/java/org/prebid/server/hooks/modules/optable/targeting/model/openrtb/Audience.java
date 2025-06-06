package org.prebid.server.hooks.modules.optable.targeting.model.openrtb;

import lombok.Value;

import java.util.List;

@Value
public class Audience {

    String provider;

    List<AudienceId> ids;

    String keyspace;

    Integer rtbSegtax;
}
