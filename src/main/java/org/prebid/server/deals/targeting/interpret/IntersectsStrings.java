package org.prebid.server.deals.targeting.interpret;

import lombok.EqualsAndHashCode;
import org.prebid.server.deals.targeting.RequestContext;
import org.prebid.server.deals.targeting.syntax.TargetingCategory;

import java.util.List;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
public class IntersectsStrings extends Intersects<String> {

    public IntersectsStrings(TargetingCategory category, List<String> values) {
        super(category, toLowerCase(values));
    }

    @Override
    public List<String> lookupActualValues(RequestContext context) {
        final List<String> values = context.lookupStrings(category);
        return values != null ? toLowerCase(values) : null;
    }

    private static List<String> toLowerCase(List<String> values) {
        return values.stream().map(String::toLowerCase).collect(Collectors.toList());
    }
}
