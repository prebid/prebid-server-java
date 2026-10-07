package org.prebid.server.hooks.modules.optable.targeting.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class App {

    String bundle;

    String ver;
}
