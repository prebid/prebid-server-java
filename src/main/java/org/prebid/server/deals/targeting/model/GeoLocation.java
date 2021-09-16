package org.prebid.server.deals.targeting.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class GeoLocation {

    Float lat;

    Float lon;
}
