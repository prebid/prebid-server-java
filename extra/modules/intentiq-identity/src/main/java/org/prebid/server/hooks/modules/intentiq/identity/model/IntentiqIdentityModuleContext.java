package org.prebid.server.hooks.modules.intentiq.identity.model;

/**
 * State carried from the processed-auction-request (enrich) hook to the auction-response
 * (impression report) hook via {@code InvocationResult#moduleContext()} within one auction.
 *
 * @param startNanos       {@link System#nanoTime()} captured at enrich-hook entry; lets the
 *                         auction-response hook record the whole-flow latency (enrich -> bid release)
 * @param abTestUuid       IIQ A/B test id returned by the resolution response, echoed back on the report
 * @param terminationCause IIQ termination cause ({@code tc}) from the resolution response, if any
 */
public record IntentiqIdentityModuleContext(long startNanos, String abTestUuid, Long terminationCause) {
}
