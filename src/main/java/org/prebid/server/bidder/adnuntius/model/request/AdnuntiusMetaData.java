package org.prebid.server.bidder.adnuntius.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Value;

@Value(staticConstructor = "of")
public class AdnuntiusMetaData {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String usi;
}
