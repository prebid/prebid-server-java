package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class DefaultDsa {

    @JsonProperty("dsarequired")
    Integer dsaRequired;

    @JsonProperty("pubrender")
    Integer pubRender;

    @JsonProperty("datatopub")
    Integer dataToPub;

    List<DsaTransparency> transparency;
}
