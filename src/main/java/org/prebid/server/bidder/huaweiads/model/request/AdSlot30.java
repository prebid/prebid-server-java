package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.util.List;

@Data
@NoArgsConstructor
public class AdSlot30 {

    String slotid;

    Integer adtype;

    Integer test;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Integer totalDuration;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Integer orientation;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Integer w;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Integer h;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<Format> format;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<String> detailedCreativeTypeList;

}
