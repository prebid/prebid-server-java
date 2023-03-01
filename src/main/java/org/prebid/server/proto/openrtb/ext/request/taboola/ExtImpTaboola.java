package org.prebid.server.proto.openrtb.ext.request.taboola;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value(staticConstructor = "of")
public class ExtImpTaboola {
    String publisherId;
    String publisherDomain;
    @JsonProperty("tagid")
    String tagId;
    BigDecimal bidfloor;
    List<String> bcat;
    List<String> badv;
}
