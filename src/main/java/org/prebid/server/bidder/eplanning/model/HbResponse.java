package org.prebid.server.bidder.eplanning.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class HbResponse {

    @JsonProperty("sp")
    List<HbResponseSpace> spaces;
}
