package org.prebid.server.deals.targeting.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@Value
@AllArgsConstructor(staticName = "of")
public class UserSegments {

    String source;

    List<String> ids;
}
