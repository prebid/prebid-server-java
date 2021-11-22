package org.prebid.server.floors.model;

import lombok.Value;

import java.util.List;

@Value
public class PriceFloorSchema {

    String delimiter;

    List<String> fields;
}
