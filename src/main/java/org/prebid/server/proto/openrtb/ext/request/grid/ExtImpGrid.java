package org.prebid.server.proto.openrtb.ext.request.grid;

import lombok.Value;
import org.prebid.server.bidder.grid.model.request.Keywords;

@Value(staticConstructor = "of")
public class ExtImpGrid {

    Integer uid;

    Keywords keywords;
}
