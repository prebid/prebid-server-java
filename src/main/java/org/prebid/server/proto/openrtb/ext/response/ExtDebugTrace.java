package org.prebid.server.proto.openrtb.ext.response;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidresponse.ext.debug.trace
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtDebugTrace {

    List<ExtTraceActivityInfrastructure> activityInfrastructure;
}
