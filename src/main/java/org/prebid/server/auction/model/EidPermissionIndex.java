package org.prebid.server.auction.model;

import com.iab.openrtb.request.Eid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidDataEidPermissions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EidPermissionIndex {

    // bitmask for which fields are present in a permission
    private static final int INSERTER = 1; // 0001
    private static final int SOURCE = 2; // 0010
    private static final int MATCHER = 4; // 0100
    private static final int MM = 8; // 1000
    private static final String WILDCARD_BIDDER = "*";

    private final Map<Integer, Map<Key, Set<String>>> ruleIndexByMask;

    private record Key(String inserter, String source, String matcher, Integer mm) {
    }

    private EidPermissionIndex(Map<Integer, Map<Key, Set<String>>> ruleIndexByMask) {
        this.ruleIndexByMask = ruleIndexByMask;
    }

    public static EidPermissionIndex build(List<ExtRequestPrebidDataEidPermissions> permissions) {
        if (ObjectUtils.isEmpty(permissions)) {
            return null;
        }

        final Map<Integer, Map<Key, Set<String>>> idx = new HashMap<>();

        for (ExtRequestPrebidDataEidPermissions permission : permissions) {
            final List<String> bidders = CollectionUtils.emptyIfNull(permission.getBidders())
                    .stream()
                    .filter(StringUtils::isNotBlank)
                    .map(String::toLowerCase)
                    .toList();

            if (bidders.isEmpty()) {
                continue;
            }

            final int ruleMask = maskOf(permission.getInserter(),
                    permission.getSource(),
                    permission.getMatcher(),
                    permission.getMm());
            final Key ruleKey = new Key(permission.getInserter(),
                    permission.getSource(),
                    permission.getMatcher(),
                    permission.getMm());

            idx.computeIfAbsent(ruleMask, ignored -> new HashMap<>())
                    .computeIfAbsent(ruleKey, ignored -> new HashSet<>())
                    .addAll(bidders);
        }

        return new EidPermissionIndex(idx);
    }

    private static int maskOf(String inserter, String source, String matcher, Integer mm) {
        int mask = 0;

        if (inserter != null) {
            mask |= INSERTER;
        }
        if (source != null) {
            mask |= SOURCE;
        }
        if (matcher != null) {
            mask |= MATCHER;
        }
        if (mm != null) {
            mask |= MM;
        }

        return mask;
    }

    public boolean isAllowed(Eid eid, String bidder) {
        final int eidMask = maskOf(eid.getInserter(), eid.getSource(), eid.getMatcher(), eid.getMm());

        boolean ruleMatched = false;

        // Check every permission bucket whose criteria fields are a subset of the Eid’s populated fields
        for (Map.Entry<Integer, Map<Key, Set<String>>> ruleBucket : ruleIndexByMask.entrySet()) {
            final int ruleMask = ruleBucket.getKey();

            // rule can only match if all its required fields exist on the Eid
            if ((ruleMask & eidMask) != ruleMask) {
                continue;
            }

            final Key normalizedEidKey = new Key((ruleMask & INSERTER) != 0 ? eid.getInserter() : null,
                    (ruleMask & SOURCE) != 0 ? eid.getSource() : null,
                    (ruleMask & MATCHER) != 0 ? eid.getMatcher() : null,
                    (ruleMask & MM) != 0 ? eid.getMm() : null);

            final Set<String> allowedBidders = ruleBucket.getValue().get(normalizedEidKey);
            if (allowedBidders != null) {
                ruleMatched = true;
                if (allowedBidders.contains(WILDCARD_BIDDER) || allowedBidders.contains(bidder.toLowerCase())) {
                    return true;
                }
            }
        }

        // allow-by-default: if no rule matched at all, allow
        return !ruleMatched;
    }
}
