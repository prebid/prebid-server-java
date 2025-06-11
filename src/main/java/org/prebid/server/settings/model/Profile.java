package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class Profile {

    Type type;

    @JsonProperty("mergeprecedence")
    MergePrecedence mergePrecedence;

    ObjectNode body;

    public enum Type {

        REQUEST, IMP
    }

    public enum MergePrecedence {

        REQUEST, PROFILE
    }
}
