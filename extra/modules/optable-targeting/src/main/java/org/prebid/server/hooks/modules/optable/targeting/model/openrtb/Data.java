package org.prebid.server.hooks.modules.optable.targeting.model.openrtb;

import lombok.Value;

import java.util.List;

@Value
public class Data {

    String id;

    List<Segment> segment;
}
