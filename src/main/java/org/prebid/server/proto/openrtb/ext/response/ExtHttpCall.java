package org.prebid.server.proto.openrtb.ext.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Defines the contract for a bidresponse.ext.debug.httpcalls.{bidder}[i]
 */
@Builder
@Value
public class ExtHttpCall {

    String uri;

    String requestbody;

    String responsebody;

    Map<String, List<String>> requestheaders;

    Integer status;
}
