package org.prebid.server.hooks.modules.ortb2.blocking.core.config;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class Attributes {

    Attribute<String> badv;

    Attribute<String> bcat;

    Attribute<String> bapp;

    Attribute<Integer> btype;

    Attribute<Integer> battr;
}
