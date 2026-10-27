package org.prebid.server.hooks.modules.intentiq.identity.cache;

import com.iab.openrtb.request.Eid;

import java.util.List;

/**
 * Outcome of a multi-key cache lookup:
 * <ul>
 *   <li>{@link State#HIT} — a positive entry was found; {@link #eids()} carries the resolved identity.</li>
 *   <li>{@link State#NEGATIVE} — a negative sentinel was found (the id is known-unresolvable); skip the
 *       upstream call and do not enrich.</li>
 *   <li>{@link State#IN_PROGRESS} — a resolution call for this id is already in flight; skip the
 *       upstream call (do not fire a duplicate) and do not enrich.</li>
 *   <li>{@link State#MISS} — nothing cached; fetch from the API.</li>
 * </ul>
 */
public record CacheResult(State state, List<Eid> eids, KeyType keyType, Layer layer) {
    public enum State {
        HIT,
        NEGATIVE,
        IN_PROGRESS,
        MISS
    }

    /** Which cache layer served the outcome: {@code L1} (in-process Caffeine) or {@code L2} (Redis). */
    public enum Layer {
        L1,
        L2
    }

    // keyType is the type of the candidate key that produced the outcome (HIT/NEGATIVE/IN_PROGRESS);
    // both keyType and layer are null for MISS, where no key/layer matched.
    private static final CacheResult MISS = new CacheResult(State.MISS, List.of(), null, null);

    public static CacheResult hit(List<Eid> eids, KeyType keyType, Layer layer) {
        return new CacheResult(State.HIT, eids, keyType, layer);
    }

    public static CacheResult negative(KeyType keyType, Layer layer) {
        return new CacheResult(State.NEGATIVE, List.of(), keyType, layer);
    }

    public static CacheResult inProgress(KeyType keyType, Layer layer) {
        return new CacheResult(State.IN_PROGRESS, List.of(), keyType, layer);
    }

    public static CacheResult miss() {
        return MISS;
    }
}
