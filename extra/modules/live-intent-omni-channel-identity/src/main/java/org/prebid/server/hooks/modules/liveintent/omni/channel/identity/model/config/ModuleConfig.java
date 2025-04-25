package org.prebid.server.hooks.modules.liveintent.omni.channel.identity.model.config;

import lombok.Data;

@Data
public final class ModuleConfig {
    Long requestTimeout;
    String idResUrl;
}
