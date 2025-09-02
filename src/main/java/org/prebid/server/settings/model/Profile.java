package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class Profile {

    Type type;

    @JsonProperty("mergeprecedence")
    @Builder.Default
    MergePrecedence mergePrecedence = MergePrecedence.REQUEST;

    JsonNode body;

    public static Profile of(Type type, MergePrecedence mergePrecedence, JsonNode body) {
        return Profile.builder()
                .type(type)
                .mergePrecedence(mergePrecedence)
                .body(body)
                .build();
    }

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
