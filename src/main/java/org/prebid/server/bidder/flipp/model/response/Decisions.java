package org.prebid.server.bidder.flipp.model.response;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class Decisions {

    List<Inline> inline;
}
