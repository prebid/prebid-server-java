package org.prebid.server.proto.openrtb.ext;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidrequest.ext.prebid.targeting.includebrandcategory
 */
@Value(staticConstructor = "of")
public class ExtIncludeBrandCategory {

    @JsonProperty("primaryadserver")
    Integer primaryAdserver;

    String publisher;

    @JsonProperty("withcategory")
    Boolean withCategory;

    @JsonProperty("translatecategories")
    Boolean translateCategories;
}
