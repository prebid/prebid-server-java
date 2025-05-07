package org.prebid.server.hooks.modules.liveintent.omni.channel.identity.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.request.Eid;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Builder
@Data
@Jacksonized
public class IdResResponse {
    @JsonProperty("eids")
    List<Eid> eids;
}
