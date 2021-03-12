package org.prebid.server.proto.openrtb.ext;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtIncludeBrandCategory {

    @JsonProperty("primaryadserver")
    Integer primaryAdserver;

    String publisher;

    @JsonProperty("withcategory")
    Boolean withCategory;

    @JsonProperty("translatecategories")
    Boolean translateCategories;
}

