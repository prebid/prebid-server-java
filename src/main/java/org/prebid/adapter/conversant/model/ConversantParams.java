package org.prebid.adapter.conversant.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public final class ConversantParams {

    String siteId;

    Integer secure;

    String tagId;

    Integer position;

    Float bidfloor;

    Integer mobile;

    List<String> mimes;

    List<Integer> api;

    List<Integer> protocols;

    Integer maxduration;
}
