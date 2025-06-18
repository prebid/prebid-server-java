package org.prebid.server.hooks.modules.rule.engine.core.rules;

import lombok.Value;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.model.UpdateResult;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.SeatNonBid;
import org.prebid.server.util.ListUtil;

import java.util.Collections;
import java.util.List;

@Value(staticConstructor = "of")
public class RuleResult<T> {

    UpdateResult<T> updateResult;

    Tags analyticsTags;

    List<SeatNonBid> seatNonBid;

    public RuleResult<T> mergeWith(RuleResult<T> other) {
        final boolean updated = other.updateResult.isUpdated() || updateResult.isUpdated();
        final T value = other.updateResult.getValue();
        final Tags tags = TagsImpl.of(ListUtil.union(analyticsTags.activities(), other.analyticsTags.activities()));
        final List<SeatNonBid> seatNonBids = ListUtil.union(seatNonBid, other.seatNonBid);

        return RuleResult.of(UpdateResult.of(updated, value), tags, seatNonBids);
    }

    public static <T> RuleResult<T> unaltered(T value) {
        return RuleResult.of(
                UpdateResult.unaltered(value), TagsImpl.of(Collections.emptyList()), Collections.emptyList());
    }
}
