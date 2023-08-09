package org.prebid.server.activity.infrastructure.privacy.usnat.debug;

import lombok.Value;
import org.prebid.server.activity.infrastructure.rule.Rule;

@Value(staticConstructor = "of")
public class USNatModuleEntryLog {

    String moduleImplementation;

    Object gppReader;

    Rule.Result precomputedResult;
}
