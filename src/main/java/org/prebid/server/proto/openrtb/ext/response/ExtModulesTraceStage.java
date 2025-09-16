package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.prebid.server.hooks.execution.model.Stage;

import java.util.List;

@Value(staticConstructor = "of")
public class ExtModulesTraceStage {

    Stage stage;

    @JsonProperty("executiontimemillis")
    Long executionTime;

    List<ExtModulesTraceStageOutcome> outcomes;
}
