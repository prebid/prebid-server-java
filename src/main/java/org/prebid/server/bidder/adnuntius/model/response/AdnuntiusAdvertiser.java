package org.prebid.server.bidder.adnuntius.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class AdnuntiusAdvertiser {

    @JsonProperty("legalName")
    String legalName;

    String name;
}
