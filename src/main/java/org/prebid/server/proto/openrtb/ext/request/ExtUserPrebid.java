package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Map;

/**
 * Defines the the contract for bidrequest.user.ext.prebid
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtUserPrebid {

    Map<String, String> buyeruids;
}
