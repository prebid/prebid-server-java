package org.prebid.server.hooks.modules.ortb2.blocking.core.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Builder
@Value
public class BlockedAttributes {

    List<String> badv;

    List<String> bcat;

    List<String> bapp;

    Map<String, List<Integer>> btype;

    Map<String, List<Integer>> battr;
}
