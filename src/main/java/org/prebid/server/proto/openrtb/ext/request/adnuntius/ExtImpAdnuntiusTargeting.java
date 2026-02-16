package org.prebid.server.proto.openrtb.ext.request.adnuntius;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class ExtImpAdnuntiusTargeting {

    @JsonProperty("c")
    List<String> category;

    List<String> segments;

    List<String> keywords;

    @JsonProperty("kv")
    Map<String, List<String>> keyValues;

    @JsonProperty("auml")
    List<String> adUnitMatchingLabel;


}
