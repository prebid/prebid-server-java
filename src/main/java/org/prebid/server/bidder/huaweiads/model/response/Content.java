package org.prebid.server.bidder.huaweiads.model.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class Content {

    String contentId;

    Integer interactionType;

    Integer creativeType;

    MetaData metaData;

    List<Monitor> monitorList;

    String cur;

    Double price;

}
