package org.prebid.server.hooks.modules.ortb2.blocking.core.model;

import lombok.Value;

import java.util.Set;

@Value(staticConstructor = "of")
public class BlockedBids {

    Set<Integer> indexes;
}
