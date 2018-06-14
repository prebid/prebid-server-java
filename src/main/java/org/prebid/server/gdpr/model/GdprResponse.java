package org.prebid.server.gdpr.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public class GdprResponse {

    Map<Integer, Boolean> vendorsToGdpr;

    String country;
}
