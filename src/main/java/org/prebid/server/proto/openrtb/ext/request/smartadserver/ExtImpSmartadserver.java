package org.prebid.server.proto.openrtb.ext.request.smartadserver;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

<<<<<<< HEAD
=======
/**
 * Defines the contract for bidrequest.imp[i].ext.smartadserver
 */
>>>>>>> 04d9d4a13 (Initial commit)
@Value(staticConstructor = "of")
public class ExtImpSmartadserver {

    @JsonProperty("siteId")
    Integer siteId;

    @JsonProperty("pageId")
    Integer pageId;

    @JsonProperty("formatId")
    Integer formatId;

    @JsonProperty("networkId")
    Integer networkId;
<<<<<<< HEAD

    @JsonProperty(value = "programmaticGuaranteed", access = JsonProperty.Access.WRITE_ONLY)
    boolean programmaticGuaranteed;
=======
>>>>>>> 04d9d4a13 (Initial commit)
}
