package org.prebid.server.auction.model;

import lombok.Value;

import java.util.Set;

@Value(staticConstructor = "of")
public class SecBrowsingTopic {

    String domain;

    Set<String> segments;

    int taxonomyVersion;

    String modelVersion;
}
