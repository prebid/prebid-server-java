package org.prebid.server.bidder.ix.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class IxDiag {

    String pbsv;

    String pbjsv;

    @JsonProperty("multipleSiteIds")
    String multipleSiteIds;
}
