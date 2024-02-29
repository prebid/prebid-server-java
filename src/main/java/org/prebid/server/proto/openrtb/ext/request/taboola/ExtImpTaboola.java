package org.prebid.server.proto.openrtb.ext.request.taboola;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Builder
@Value
public class ExtImpTaboola {

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("publisherDomain")
    String publisherDomain;

    @JsonProperty("tagid")
    String lowerCaseTagId;

    @JsonProperty("tagId")
    String tagId;

    @JsonProperty("bidfloor")
    BigDecimal bidFloor;

    @JsonProperty("bcat")
    List<String> bCat;

    @JsonProperty("badv")
    List<String> bAdv;

    @JsonProperty("pageType")
    String pageType;

    @JsonProperty("position")
    Integer position;

    public static ExtImpTaboola empty() {
        return builder().build();
    }
}
