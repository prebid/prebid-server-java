package org.prebid.server.bidder.eplanning.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class CleanStepName {

    String expression;

    String replacementString;
}
