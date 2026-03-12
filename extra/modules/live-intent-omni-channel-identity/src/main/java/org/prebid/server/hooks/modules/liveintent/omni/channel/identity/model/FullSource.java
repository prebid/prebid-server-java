package org.prebid.server.hooks.modules.liveintent.omni.channel.identity.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class FullSource {

    String source;

    String inserter;
}
