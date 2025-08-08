package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class Profile {

    Type type;

    @JsonProperty("mergeprecedence")
    MergePrecedence mergePrecedence;

    JsonNode body;

    public enum Type {

        @JsonAlias("request")
        REQUEST,

        @JsonAlias("imp")
        IMP
    }

    public enum MergePrecedence {

        @JsonAlias("request")
        REQUEST,

        @JsonAlias("profile")
        PROFILE
    }
}
