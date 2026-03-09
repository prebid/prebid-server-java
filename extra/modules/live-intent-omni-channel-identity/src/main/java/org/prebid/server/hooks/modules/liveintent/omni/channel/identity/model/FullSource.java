package org.prebid.server.hooks.modules.liveintent.omni.channel.identity.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class FullSource {

    String source;
    String inserter;
}
