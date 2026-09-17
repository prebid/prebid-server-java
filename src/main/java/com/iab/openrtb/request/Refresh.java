package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.List;

@Builder
@Value
@NonFinal
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Refresh {

    /**
     * A RefSettings object describing the mechanics of how an ad placement automatically refreshes.
     */
    List<RefSettings> refsettings;

    /**
     * The number of times this ad slot had been refreshed since last page load.
     */
    Integer count;

    /**
     * Placeholder for vendor specific extensions to this object.
     */
    ObjectNode ext;
}
