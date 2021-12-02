package org.prebid.server.floors.model;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class PriceFloorSchema {

    String delimiter;

    List<PriceFloorField> fields;
}
