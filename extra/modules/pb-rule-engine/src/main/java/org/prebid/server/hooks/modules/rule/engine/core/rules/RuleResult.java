package org.prebid.server.hooks.modules.rule.engine.core.rules;

import lombok.Value;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.SeatNonBid;
import org.prebid.server.util.ListUtil;

import java.util.Collections;
import java.util.List;

@Value(staticConstructor = "of")
public class RuleResult<T> {

    T value;

    RuleAction action;

    Tags analyticsTags;

    List<SeatNonBid> seatNonBid;

    public RuleResult<T> mergeWith(RuleResult<T> other) {
        final T value = other.getValue();
        final RuleAction action = merge(this.action, other.getAction());
        final Tags tags = TagsImpl.of(ListUtil.union(analyticsTags.activities(), other.analyticsTags.activities()));
        final List<SeatNonBid> seatNonBids = ListUtil.union(seatNonBid, other.seatNonBid);

        return RuleResult.of(value, action, tags, seatNonBids);
    }

    private static RuleAction merge(RuleAction left, RuleAction right) {
        if (left == RuleAction.REJECT || right == RuleAction.REJECT) {
            return RuleAction.REJECT;
        }
        if (left == RuleAction.UPDATE || right == RuleAction.UPDATE) {
            return RuleAction.UPDATE;
        }
        return RuleAction.NO_ACTION;
    }

    public boolean isReject() {
        return action == RuleAction.REJECT;
    }

    public boolean isUpdate() {
        return action == RuleAction.UPDATE;
    }

    public static <T> RuleResult<T> noAction(T value) {
        return RuleResult.of(
                value, RuleAction.NO_ACTION, TagsImpl.of(Collections.emptyList()), Collections.emptyList());
    }

    public static <T> RuleResult<T> rejected(Tags analyticsTags, List<SeatNonBid> seatNonBids) {
        return RuleResult.of(null, RuleAction.REJECT, analyticsTags, seatNonBids);
    }
}
