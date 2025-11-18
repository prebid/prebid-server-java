package org.prebid.server.bidder.eplanning.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class CleanStepName {

    String expression;

    String replacementString;
}
