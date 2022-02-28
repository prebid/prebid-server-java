package org.prebid.server.deals.targeting;

import lombok.Value;
import org.prebid.server.deals.targeting.interpret.Expression;

@Value(staticConstructor = "of")
public class TargetingDefinition {

    Expression rootExpression;
}
