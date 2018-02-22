package org.prebid.model.openrtb.ext.response;

import lombok.Builder;
import lombok.Value;

/**
 * Defines the contract for a bidresponse.ext.debug.httpcalls.{bidder}[i]
 */
@Builder
@Value
public final class ExtHttpCall {

    String uri;

    String requestbody;

    String responsebody;

    Integer status;
}
