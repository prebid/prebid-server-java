package org.prebid.server.hooks.modules.liveintent.omni.channel.identity.model;

import com.iab.openrtb.request.Eid;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
public class IdResResponse {
    List<Eid> eids;
}
