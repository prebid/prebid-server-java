package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidrequest.ext.prebid.data
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtRequestPrebidData {

    /**
     * Defines the contract for bidrequest.ext.prebid.data.bidders
     */
    List<String> bidders;

    @JsonProperty("eidpermissions")
    @JsonFormat(without = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    List<ExtRequestPrebidDataEidPermissions> eidPermissions;
}
