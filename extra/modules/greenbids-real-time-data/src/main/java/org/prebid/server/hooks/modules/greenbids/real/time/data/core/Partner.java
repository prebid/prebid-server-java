package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.OnnxModelRunner;

@Builder(toBuilder = true)
@Value
public class Partner {

    String pbuid;

    double explorationRate;

    double targetTpr;

    OnnxModelRunner onnxModelRunner;

    String thresholdsJsonPath;
}
