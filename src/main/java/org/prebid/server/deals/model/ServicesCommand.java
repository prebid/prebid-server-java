package org.prebid.server.deals.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ServicesCommand {

    String cmd;
}
