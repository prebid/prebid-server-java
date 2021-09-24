package org.prebid.server.hooks.execution.model;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class StageExecutionOutcome {

    String entity;

    List<GroupExecutionOutcome> groups;
}
