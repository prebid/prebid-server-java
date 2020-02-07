package org.prebid.server.proto.openrtb.ext.response;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidresponse.ext.debug
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtResponseVideoTargeting {

    String hbPb;

    String hbPbCatDur;

    String hbCacheID;
}

