package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ExtRequestPrebidDataEidPermissions {

    /**
     * Defines the contract for bidrequest.ext.prebid.data.eidPermissions.inserter
     */
    String inserter;

    /**
     * Defines the contract for bidrequest.ext.prebid.data.eidPermissions.source
     */
    String source;

    /**
     * Defines the contract for bidrequest.ext.prebid.data.eidPermissions.matcher
     */
    String matcher;

    /**
     * Defines the contract for bidrequest.ext.prebid.data.eidPermissions.mm
     */
    Integer mm;

    /**
     * Defines the contract for bidrequest.ext.prebid.data.eidPermissions.bidders
     */
    @JsonFormat(without = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    List<String> bidders;

    @Deprecated
    public static ExtRequestPrebidDataEidPermissions of(String source, List<String> bidders) {
        return new ExtRequestPrebidDataEidPermissions(null, source, null, null, bidders);
    }
}
