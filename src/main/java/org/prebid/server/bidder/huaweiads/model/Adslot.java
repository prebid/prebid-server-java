package org.prebid.server.bidder.huaweiads.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Setter;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Builder
@Setter
@Getter
public class Adslot {
    private String slotId;
    private Integer adType;
    private Integer test;
    private Integer totalDuration;
    private Integer orientation;
    private Integer w;
    private Integer h;
    private List<Format> format;
    private List<String> detailedCreativeTypeList;
}
