package org.prebid.server.deals.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class UserData {

    String id;

    String name;

    List<Segment> segment;
}
