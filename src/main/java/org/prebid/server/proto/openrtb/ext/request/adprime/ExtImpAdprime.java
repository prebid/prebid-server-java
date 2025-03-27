package org.prebid.server.proto.openrtb.ext.request.adprime;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidRequest.imp[i].ext.adprime
 */
@Value(staticConstructor = "of")
public class ExtImpAdprime {

    @JsonProperty("TagID")
    String tagId;

    List<String> keywords;

    List<String> audiences;
}
