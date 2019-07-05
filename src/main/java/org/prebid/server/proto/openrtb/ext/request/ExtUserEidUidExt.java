package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.user.ext.eids[].uids[].ext
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtUserEidUidExt {

    @JsonProperty("rtiPartner")
    String rtiPartner;
}
