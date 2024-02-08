package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class DsaTransparency {

    String domain;

    @JsonProperty("dsaparams")
    List<Integer> dsaParams;
}
