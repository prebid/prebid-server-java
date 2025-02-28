package org.prebid.server.hooks.modules.optable.targeting.model.openrtb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value
public class Audience {

    String provider;

    List<AudienceId> ids;

    String keyspace;

    @JsonProperty("rtb_segtax")
    Integer rtbSegtax;
}
