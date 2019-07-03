package org.prebid.server.proto.openrtb.ext.request.adform;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.adform
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpAdform {

    /**
     * Defines the contract for bidrequest.imp[i].ext.adform.mid
     */
    @JsonProperty("mid")
    Long masterTagId;

    @JsonProperty("priceType")
    String priceType;

    @JsonProperty("mkv")
    String keyValues;

    @JsonProperty("mkw")
    String keyWords;
}
