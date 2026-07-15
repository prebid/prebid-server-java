package org.prebid.server.auction;

import com.iab.openrtb.request.Eid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidDataEidPermissions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EidPermissionResolver {

    private static final String WILDCARD_BIDDER = "*";

    private static final ExtRequestPrebidDataEidPermissions DEFAULT_RULE = ExtRequestPrebidDataEidPermissions.builder()
            .bidders(Collections.singletonList(WILDCARD_BIDDER))
            .build();

    private static final EidPermissionResolver EMPTY = new EidPermissionResolver(Collections.emptyList());

    private final List<ExtRequestPrebidDataEidPermissions> eidPermissions;

    private EidPermissionResolver(List<ExtRequestPrebidDataEidPermissions> eidPermissions) {
        this.eidPermissions = new ArrayList<>(eidPermissions);
        this.eidPermissions.add(DEFAULT_RULE);
    }

    public static EidPermissionResolver of(List<ExtRequestPrebidDataEidPermissions> eidPermissions) {
        return new EidPermissionResolver(eidPermissions);
    }

    public static EidPermissionResolver empty() {
        return EMPTY;
    }

    public List<Eid> resolveAllowedEids(List<Eid> userEids, String bidder) {
        return CollectionUtils.emptyIfNull(userEids)
                .stream()
                .filter(userEid -> isAllowed(userEid, bidder))
                .toList();
    }

    private boolean isAllowed(Eid eid, String bidder) {
        final Map<Integer, List<ExtRequestPrebidDataEidPermissions>> matchingRulesBySpecificity = eidPermissions
                .stream()
                .filter(rule -> isRuleMatched(eid, rule))
                .collect(Collectors.groupingBy(this::getRuleSpecificity));

        final int highestSpecificityMatchingRules = Collections.max(matchingRulesBySpecificity.keySet());
        return matchingRulesBySpecificity.get(highestSpecificityMatchingRules).stream()
                .anyMatch(eidPermission -> isBidderAllowed(bidder, eidPermission.getBidders()));
    }

    private int getRuleSpecificity(ExtRequestPrebidDataEidPermissions eidPermission) {
        return (int) Stream.of(
                        eidPermission.getInserter(),
                        eidPermission.getSource(),
                        eidPermission.getMatcher(),
                        eidPermission.getMm())
                .filter(Objects::nonNull)
                .count();
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
