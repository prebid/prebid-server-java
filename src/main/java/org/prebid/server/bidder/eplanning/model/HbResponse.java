package org.prebid.server.bidder.eplanning.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class HbResponse {

    @JsonProperty("sp")
    List<HbResponseSpace> spaces;
}
