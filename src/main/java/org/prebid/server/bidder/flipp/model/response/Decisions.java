package org.prebid.server.bidder.flipp.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class Decisions {

    @JsonProperty("inline")
    List<Inline> inline;
}
