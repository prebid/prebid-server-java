package org.prebid.server.proto.openrtb.ext.request.grid;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class KeywordsPublisherItem {

    String name;

    List<KeywordSegment> segments;
}
