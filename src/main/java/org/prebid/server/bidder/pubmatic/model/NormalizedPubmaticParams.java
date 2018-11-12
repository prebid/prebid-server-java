package org.prebid.server.bidder.pubmatic.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class NormalizedPubmaticParams {

    String publisherId;

    String adSlot;

    String tagId;

    Integer width;

    Integer height;

    ObjectNode wrapExt;

    ObjectNode keywords;
}
