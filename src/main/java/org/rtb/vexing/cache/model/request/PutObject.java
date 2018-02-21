package org.rtb.vexing.cache.model.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class PutObject {

    String type;

    JsonNode value;
}
