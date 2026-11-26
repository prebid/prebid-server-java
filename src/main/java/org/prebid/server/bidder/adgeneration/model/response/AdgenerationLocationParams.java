package org.prebid.server.bidder.adgeneration.model.response;

import lombok.Value;

@Value(staticConstructor = "of")
public class AdgenerationLocationParams {

    AdgenerationOption option;
}
