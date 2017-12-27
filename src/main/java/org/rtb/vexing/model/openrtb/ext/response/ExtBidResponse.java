package org.rtb.vexing.model.openrtb.ext.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

/**
 * Defines the contract for bidresponse.ext
 */
@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class ExtBidResponse {

    ExtResponseDebug debug;

    /**
     * Defines the contract for bidresponse.ext.errors
     */
    Map<String, List<String>> errors;

    /**
     * Defines the contract for bidresponse.ext.responsetimemillis
     */
    Map<String, Integer> responsetimemillis;

    /**
     * Defines the contract for bidresponse.ext.usersync
     */
    Map<String, ExtResponseSyncData> usersync;
}
