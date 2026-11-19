package org.prebid.server.hooks.modules.intentiq.identity.v1.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.intentiq.identity.model.config.IntentiqIdentityProperties;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;

import java.util.List;

/**
 * Picks the IntentIQ partner id (dpi) of one auction.
 *
 * <p>A host can send the partner id in the bid request as {@code ext.intentiq.partnerId} (a string or a
 * number). It is used only when it is listed in {@code allowed-partner-ids}, so a caller can never act as a
 * partner the host did not list. Otherwise, or when the request carries none, the configured
 * {@code partner-id} (account level over host level) is used.
 *
 * <p>The {@code ext.intentiq} object is meant for this module only: {@link #withoutRequestField} removes it
 * before the request goes to the bidders.
 */
public final class PartnerIdResolver {

    public static final String EXT_FIELD = "intentiq";
    public static final String PARTNER_ID_FIELD = "partnerId";

    private PartnerIdResolver() {
    }

    /**
     * @param partnerId       the partner id to use, or null when there is none
     * @param requestRejected true when the request carried a partner id that is not in the allowlist
     */
    public record Resolved(String partnerId, boolean requestRejected) {
    }

    public static Resolved resolve(IntentiqIdentityProperties properties, BidRequest bidRequest) {
        final String configured = StringUtils.trimToNull(properties.getPartnerId());
        final String requested = fromRequest(bidRequest);
        if (requested == null) {
            return new Resolved(configured, false);
        }

        final List<String> allowed = properties.getAllowedPartnerIds();
        if (allowed != null && allowed.stream().anyMatch(id -> requested.equals(StringUtils.trim(id)))) {
            return new Resolved(requested, false);
        }

        return new Resolved(configured, true);
    }

    /** The trimmed {@code ext.intentiq.partnerId} of the request, or null when it is missing or blank. */
    public static String fromRequest(BidRequest bidRequest) {
        final JsonNode intentiq = requestField(bidRequest);
        final JsonNode partnerId = intentiq != null ? intentiq.get(PARTNER_ID_FIELD) : null;
        if (partnerId == null || !(partnerId.isTextual() || partnerId.isIntegralNumber())) {
            return null;
        }

        return StringUtils.trimToNull(partnerId.asText());
    }

    public static boolean hasRequestField(BidRequest bidRequest) {
        final ExtRequest ext = bidRequest != null ? bidRequest.getExt() : null;
        return ext != null && ext.containsProperty(EXT_FIELD);
    }

    /** A copy of the request without {@code ext.intentiq}; the request itself is not changed. */
    public static BidRequest withoutRequestField(BidRequest bidRequest) {
        if (!hasRequestField(bidRequest)) {
            return bidRequest;
        }

        final ExtRequest ext = bidRequest.getExt();
        final ExtRequest stripped = ExtRequest.of(ext.getPrebid());
        stripped.addProperties(ext.getProperties());
        stripped.removeProperty(EXT_FIELD);
        return bidRequest.toBuilder().ext(stripped).build();
    }

    private static JsonNode requestField(BidRequest bidRequest) {
        final ExtRequest ext = bidRequest != null ? bidRequest.getExt() : null;
        return ext != null ? ext.getProperty(EXT_FIELD) : null;
    }
}
