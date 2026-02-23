package com.iab.openrtb.request.video;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class IncludeBrandCategory {

    @JsonProperty("primaryadserver")
    Integer primaryAdserver;

    String publisher;

    @JsonProperty("translatecategories")
    Boolean translateCategories;
}
