package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;

import java.util.Map;

/**
 * Defines the contract for bidrequest.user.ext.prebid
 */
@Value(staticConstructor = "of")
public class ExtUserPrebid {

    Map<String, String> buyeruids;
}
