package org.prebid.server.deals.targeting.interpret;

import lombok.EqualsAndHashCode;
import org.prebid.server.deals.targeting.RequestContext;
import org.prebid.server.deals.targeting.syntax.TargetingCategory;

import java.util.List;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
public class InStrings extends In<String> {

    public InStrings(TargetingCategory category, List<String> values) {
        super(category, toLowerCase(values));
    }

    @Override
    public String lookupActualValue(RequestContext context) {
        final String actualValue = context.lookupString(category);

        return actualValue != null ? actualValue.toLowerCase() : null;
    }

    private static List<String> toLowerCase(List<String> values) {
        return values.stream().map(String::toLowerCase).collect(Collectors.toList());
    }
}
