package org.prebid.server.deals.targeting;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.deals.targeting.interpret.Expression;

@AllArgsConstructor(staticName = "of")
@Value
public class TargetingDefinition {

    private final Expression rootExpression;
}
