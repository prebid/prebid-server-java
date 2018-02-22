package org.prebid.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class UsersyncInfo {

    String url;

    String type;

    @JsonProperty("supportCORS")
    Boolean supportCORS;
}
