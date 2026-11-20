package org.prebid.server.hooks.modules.intentiq.identity.model;

/**
 * Module context set by the raw-auction-request hook: the {@code ext.intentiq.partnerId} it took out of the
 * request before any other stage ran. The enrich hook replaces it with an {@link IntentiqIdentityModuleContext};
 * when the enrich hook timed out, the report hook still finds the requested partner id here.
 *
 * @param partnerId the trimmed requested partner id, or null when the request carried none; not yet checked
 *                  against {@code allowed-partner-ids}
 */
public record RequestPartnerContext(String partnerId) {
}
