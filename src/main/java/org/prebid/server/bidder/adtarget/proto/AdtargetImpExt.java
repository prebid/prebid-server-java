package org.prebid.server.bidder.adtarget.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.adtarget.ExtImpAdtarget;

@AllArgsConstructor(staticName = "of")
@Value
public class AdtargetImpExt {

    @JsonProperty("adtarget")
    ExtImpAdtarget extImpAdtarget;
}
