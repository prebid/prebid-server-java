package org.prebid.server.auction.model;

import com.iab.openrtb.request.Eid;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidDataEidPermissions;

import java.util.List;

public final class EidPermissionHolder {

    private static final String WILDCARD_BIDDER = "*";

    private final List<ExtRequestPrebidDataEidPermissions> eidPermissions;

    public EidPermissionHolder(List<ExtRequestPrebidDataEidPermissions> eidPermissions) {
        this.eidPermissions = eidPermissions;
    }

    public boolean isAllowed(Eid eid, String bidder) {
        if (ObjectUtils.isEmpty(eidPermissions)) {
            return true;
        }

        boolean isBestMatch = false;
        int bestSpecificity = -1;

        for (ExtRequestPrebidDataEidPermissions eidPermission : eidPermissions) {
            if (!isRuleMatched(eid, eidPermission)) {
                continue;
            }

            final int ruleSpecificity = getRuleSpecificity(eidPermission);

            final boolean isBidderAllowed = isBidderAllowed(bidder, eidPermission.getBidders());

            if (ruleSpecificity > bestSpecificity) {
                bestSpecificity = ruleSpecificity;
                isBestMatch = isBidderAllowed;
            } else if (ruleSpecificity == bestSpecificity) {
                isBestMatch |= isBidderAllowed;
            }
        }

        return bestSpecificity == -1 || isBestMatch;
    }

    private int getRuleSpecificity(ExtRequestPrebidDataEidPermissions eidPermission) {
        int specificity = 0;
        if (eidPermission.getInserter() != null) {
            specificity++;
        }
        if (eidPermission.getSource() != null) {
            specificity++;
        }
        if (eidPermission.getMatcher() != null) {
            specificity++;
        }
        if (eidPermission.getMm() != null) {
            specificity++;
        }
        return specificity;
    }

    private boolean isRuleMatched(Eid eid, ExtRequestPrebidDataEidPermissions eidPermission) {
        return (eidPermission.getInserter() == null || eidPermission.getInserter().equals(eid.getInserter()))
                && (eidPermission.getSource() == null || eidPermission.getSource().equals(eid.getSource()))
                && (eidPermission.getMatcher() == null || eidPermission.getMatcher().equals(eid.getMatcher()))
                && (eidPermission.getMm() == null || eidPermission.getMm().equals(eid.getMm()));
    }

    private boolean isBidderAllowed(String bidder, List<String> ruleBidders) {
        return ruleBidders == null || ruleBidders.stream()
                .anyMatch(allowedBidder -> StringUtils.equalsIgnoreCase(allowedBidder, bidder)
                        || WILDCARD_BIDDER.equals(allowedBidder));
    }
}
