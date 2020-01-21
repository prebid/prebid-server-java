package org.prebid.server.bidder.rubicon.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class RubiconVideoExt {

    Integer skip;

    Integer skipdelay;

    RubiconVideoExtRp rp;

    @JsonProperty("videotype")
    String videoType;
}
