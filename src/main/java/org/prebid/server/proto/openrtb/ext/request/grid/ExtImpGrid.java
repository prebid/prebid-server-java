package org.prebid.server.proto.openrtb.ext.request.grid;

import lombok.Value;
import org.prebid.server.bidder.grid.model.Keywords;

@Value(staticConstructor = "of")
public class ExtImpGrid {

    Integer uid;

    Keywords keywords;
}
