package org.prebid.server.hooks.modules.liveintent.omni.channel.identity.model;

import com.iab.openrtb.request.Eid;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class IdResResponse {

    List<Eid> eids;
}
