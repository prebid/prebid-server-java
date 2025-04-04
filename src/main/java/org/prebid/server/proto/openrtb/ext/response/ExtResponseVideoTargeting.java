package org.prebid.server.proto.openrtb.ext.response;

import lombok.Value;

/**
 * Defines the contract for bidresponse.ext.debug
 */
@Value(staticConstructor = "of")
public class ExtResponseVideoTargeting {

    String hbPb;

    String hbPbCatDur;

    String hbCacheID;
}
