package org.prebid.server.hooks.execution;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.prebid.server.hooks.execution.model.GroupExecutionOutcome;
import org.prebid.server.hooks.execution.model.StageExecutionOutcome;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
                .collect(Collectors.toList());
    }
}
