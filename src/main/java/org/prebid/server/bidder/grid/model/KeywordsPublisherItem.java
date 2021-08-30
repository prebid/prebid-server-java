package org.prebid.server.bidder.grid.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@Value
@AllArgsConstructor(staticName = "of")
public class KeywordsPublisherItem {

    String name;

    List<KeywordSegment> segments;
}
