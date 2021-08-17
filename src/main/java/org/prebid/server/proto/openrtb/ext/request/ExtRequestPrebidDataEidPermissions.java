package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidrequest.ext.prebid.data.eidPermissions
 */
@Value(staticConstructor = "of")
public class ExtRequestPrebidDataEidPermissions {

    /**
     * Defines the contract for bidrequest.ext.prebid.data.eidPermissions.source
     */
    String source;

    /**
     * Defines the contract for bidrequest.ext.prebid.data.eidPermissions.bidders
     */
    @JsonFormat(without = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    List<String> bidders;
}

