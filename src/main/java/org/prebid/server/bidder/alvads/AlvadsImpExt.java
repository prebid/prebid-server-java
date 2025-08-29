package org.prebid.server.bidder.alvads;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@JsonIgnoreProperties(ignoreUnknown = true)
@Value(staticConstructor = "of")
public class AlvadsImpExt {

    @JsonProperty("publisherUniqueId")
    String publisherUniqueId;

    @JsonProperty("endPointUrl")
    String endPointUrl;
}
