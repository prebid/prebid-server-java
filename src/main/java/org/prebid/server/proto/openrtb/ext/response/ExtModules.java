package org.prebid.server.proto.openrtb.ext.response;

import lombok.Value;

import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class ExtModules {

    Map<String, Map<String, List<String>>> errors;

    Map<String, Map<String, List<String>>> warnings;

    ExtModulesTrace trace;
}
