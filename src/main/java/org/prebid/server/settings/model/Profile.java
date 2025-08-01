package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class Profile {

    Type type;

    @JsonProperty("mergeprecedence")
    MergePrecedence mergePrecedence;

    String body;

    public enum Type {

        REQUEST, IMP
    }

    public enum MergePrecedence {

        REQUEST, PROFILE
    }
}
