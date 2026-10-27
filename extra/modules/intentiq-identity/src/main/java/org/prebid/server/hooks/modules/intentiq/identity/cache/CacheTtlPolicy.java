package org.prebid.server.hooks.modules.intentiq.identity.cache;

/**
 * TTL policy for cached identity entries. The IntentIQ API {@code cttl} (or the configured default
 * when absent) always wins, but is capped by a per-{@link KeyType} ceiling — we cache the volatile
 * resolved eids, not the stable cookie mapping, so ceilings are upper bounds only and deliberately
 * far shorter than the IntentIQ backend's mapping TTLs. Negative (unresolvable) entries and the
 * in-progress marker each use a separate short TTL.
 */
public record CacheTtlPolicy(long defaultTtlMs,
                             long firstPartyCeilingMs,
                             long thirdPartyCeilingMs,
                             long deviceCeilingMs,
                             long negativeTtlMs,
                             long inProgressTtlMs) {
    public long ceilingFor(KeyType type) {
        return switch (type) {
            case FIRST_PARTY -> firstPartyCeilingMs;
            case THIRD_PARTY -> thirdPartyCeilingMs;
            case DEVICE -> deviceCeilingMs;
        };
    }

    /**
     * Effective positive TTL for a key: {@code min(cttl-or-default, ceiling(type))}.
     */
    public long effectiveTtlMs(KeyType type, long cttlMs) {
        final long base = cttlMs > 0 ? cttlMs : defaultTtlMs;
        return Math.min(base, ceilingFor(type));
    }

    /**
     * Suppression TTL for a negative (unresolvable) entry. On an empty/invalid response the IntentIQ
     * backend signals how long to suppress re-querying this user via {@code cttl}; honor it when present
     * (bounded by the first-party ceiling as a safety cap against absurd values), else fall back to the
     * configured default negative TTL.
     */
    public long negativeTtlMs(long cttlMs) {
        return cttlMs > 0 ? Math.min(cttlMs, firstPartyCeilingMs) : negativeTtlMs;
    }
}
