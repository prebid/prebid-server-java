package org.prebid.server.bidder.adtelligent.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.adtelligent.ExtImpAdtelligent;

@AllArgsConstructor(staticName = "of")
@Value
public class AdtelligentImpExt {

    @JsonProperty("adtelligent")
    ExtImpAdtelligent extImpAdtelligent;
}
