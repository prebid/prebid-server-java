package org.prebid.server.activity.infrastructure.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.prebid.server.activity.infrastructure.payload.impl.BasicActivityInvocationPayload;

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes(@JsonSubTypes.Type(BasicActivityInvocationPayload.class))
public interface GpcActivityInvocationPayload extends ActivityInvocationPayload {

    @JsonProperty
    String gpc();
}
