package org.prebid.server.bidder.conversant.proto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class ConversantParams {

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
