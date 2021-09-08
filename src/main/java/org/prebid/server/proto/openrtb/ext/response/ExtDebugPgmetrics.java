package org.prebid.server.proto.openrtb.ext.response;

import lombok.Builder;
import lombok.Value;

import java.util.Map;
import java.util.Set;

/**
 * Defines the contract for bidresponse.ext.debug.pgmetrics
 */
@Builder
@Value
public class ExtDebugPgmetrics {

    public static final ExtDebugPgmetrics EMPTY = ExtDebugPgmetrics.builder().build();

    Set<String> sentToClient;

    Set<String> sentToClientAsTopMatch;

    Set<String> matchedDomainTargeting;

    Set<String> matchedWholeTargeting;

    Set<String> matchedTargetingFcapped;

    Set<String> matchedTargetingFcapLookupFailed;

    Set<String> readyToServe;

    Set<String> pacingDeferred;

    Map<String, Set<String>> sentToBidder;

    Map<String, Set<String>> sentToBidderAsTopMatch;

    Map<String, Set<String>> receivedFromBidder;

    Set<String> responseInvalidated;
}
