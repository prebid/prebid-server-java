package org.prebid.server.bidder.alvads.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
public class AlvaAdsImp {

    private String id;
    private Map<String, Object> banner;
    private Map<String, Object> video;
    private String tagid;
    private BigDecimal bidfloor;
}
