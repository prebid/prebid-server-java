package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.time.ZonedDateTime;

/**
 * Defines the contract for bidresponse.ext.debug.trace.deals[]
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtTraceDeal {

    /**
     * Defines the contract for bidresponse.ext.debug.trace.deals[].lineitemid
     */
    @JsonProperty("lineitemid")
    String lineItemId;

    /**
     * Defines the contract for bidresponse.ext.debug.trace.deals[].time
     */
    ZonedDateTime time;

    /**
     * Defines the contract for bidresponse.ext.debug.trace.deals[].category
     */
    Category category;

    /**
     * Defines the contract for bidresponse.ext.debug.trace.deals[].message
     */
    String message;

    public enum Category {
        targeting, pacing, cleanup, post_processing
    }
}
