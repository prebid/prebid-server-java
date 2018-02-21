package org.rtb.vexing.adapter.rubicon.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public final class RubiconParams {

    Integer accountId;

    Integer siteId;

    Integer zoneId;

    JsonNode inventory;

    JsonNode visitor;

    RubiconVideoParams video;
}
