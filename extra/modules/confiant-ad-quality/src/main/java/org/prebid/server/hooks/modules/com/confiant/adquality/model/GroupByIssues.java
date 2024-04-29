package org.prebid.server.hooks.modules.com.confiant.adquality.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class GroupByIssues<T> {

    List<T> withIssues;

    List<T> withoutIssues;
}
