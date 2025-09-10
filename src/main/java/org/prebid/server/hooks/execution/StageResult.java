package org.prebid.server.hooks.execution;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.prebid.server.auction.model.Rejection;
import org.prebid.server.hooks.execution.model.GroupExecutionOutcome;
import org.prebid.server.hooks.execution.model.StageExecutionOutcome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Accessors(fluent = true)
@Getter
class StageResult<T> {

    private boolean shouldReject;

    private T payload;
    private final String entity;

    private final List<GroupResult<T>> groupResults = new ArrayList<>();

    private StageResult(T payload, String entity) {
        this.shouldReject = false;
        this.payload = payload;
        this.entity = entity;
    }

    public static <T> StageResult<T> of(T payload, String entity) {
        return new StageResult<>(payload, entity);
    }

    public StageResult<T> applyGroupResult(GroupResult<T> groupResult) {
        groupResults.add(groupResult);

        shouldReject = groupResult.shouldReject();
        payload = groupResult.payload();

        return this;
    }

    public StageExecutionOutcome toStageExecutionOutcome() {
        return StageExecutionOutcome.of(entity, groupExecutionOutcomes());
    }

    private List<GroupExecutionOutcome> groupExecutionOutcomes() {
        return groupResults.stream()
                .map(GroupResult::toGroupExecutionOutcome)
                .toList();
    }

    public Map<String, List<Rejection>> rejections() {
        return groupResults.stream()
                .map(GroupResult::rejections)
                .reduce(StageResult::collectionMerge)
                .orElse(Collections.emptyMap());
    }

    private static Map<String, List<Rejection>> collectionMerge(Map<String, List<Rejection>> left,
                                                                Map<String, List<Rejection>> right) {

        final Map<String, List<Rejection>> merged = new HashMap<>();
        left.forEach((key, value) -> merged.put(key, new ArrayList<>(value)));
        right.forEach((key, value) -> merged.computeIfAbsent(key, k -> new ArrayList<>()).addAll(value));
        return Collections.unmodifiableMap(merged);
    }
}
