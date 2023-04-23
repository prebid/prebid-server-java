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

    Integer totalDuration;

    Integer orientation;

    Integer w;

    Integer h;

    List<Format> format;

    List<String> detailedCreativeTypeList;

}
