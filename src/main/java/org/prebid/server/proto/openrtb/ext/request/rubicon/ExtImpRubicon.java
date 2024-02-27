package org.prebid.server.proto.openrtb.ext.request.rubicon;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;

import java.util.List;
import java.util.Set;

/**
 * Defines the contract for bidrequest.imp[i].ext.rubicon
 */
@Builder
@Value
public class ExtImpRubicon {

    @JsonProperty("accountId")
    Integer accountId;

    @JsonProperty("siteId")
    Integer siteId;

    @JsonProperty("zoneId")
    Integer zoneId;

    @JsonProperty("bidonmultiformat")
    Boolean bidOnMultiFormat;

    List<Integer> sizes;

    ObjectNode inventory;

    ObjectNode visitor;

    RubiconVideoParams video;

    String pchain;

    List<String> keywords;

    Set<ImpMediaType> formats;

    ExtImpRubiconDebug debug;
}
