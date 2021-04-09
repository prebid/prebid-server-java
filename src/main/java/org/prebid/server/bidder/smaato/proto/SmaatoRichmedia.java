package org.prebid.server.bidder.smaato.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class SmaatoRichmedia {

    SmaatoMediaData mediadata;

    @JsonProperty("impressiontrackers")
    List<String> impressionTrackers;

    @JsonProperty("clicktrackers")
    List<String> clickTrackers;
}
