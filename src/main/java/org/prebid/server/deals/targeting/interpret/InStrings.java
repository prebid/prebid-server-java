package org.prebid.server.deals.targeting.interpret;

import lombok.EqualsAndHashCode;
import org.prebid.server.deals.targeting.RequestContext;
import org.prebid.server.deals.targeting.syntax.TargetingCategory;
import org.prebid.server.util.ObjectUtil;

import java.util.List;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
public class InStrings extends In<String> {

    public InStrings(TargetingCategory category, List<String> values) {
        super(category, toLowerCase(values));
    }

    @Override
    public String lookupActualValue(RequestContext context) {
        final String actualValue = ObjectUtil.firstNonNull(
                () -> context.lookupString(category),
                () -> lookupIntegerAsString(context));

        return actualValue != null ? actualValue.toLowerCase() : null;
    }

    private String lookupIntegerAsString(RequestContext context) {
        final Integer actualValue = context.lookupInteger(category);
        return actualValue != null ? actualValue.toString() : null;
    }

    private static List<String> toLowerCase(List<String> values) {
        return values.stream().map(String::toLowerCase).collect(Collectors.toList());
    }
}
