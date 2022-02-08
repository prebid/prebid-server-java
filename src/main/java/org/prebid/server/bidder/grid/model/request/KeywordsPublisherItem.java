package org.prebid.server.bidder.grid.model.request;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class KeywordsPublisherItem {

    String name;

    List<KeywordSegment> segments;
}
