package org.prebid.server.deals.targeting.interpret;

import lombok.EqualsAndHashCode;
import org.prebid.server.deals.targeting.RequestContext;
import org.prebid.server.deals.targeting.model.LookupResult;
import org.prebid.server.deals.targeting.syntax.TargetingCategory;

import java.util.List;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
public class IntersectsStrings extends Intersects<String> {

    public IntersectsStrings(TargetingCategory category, List<String> values) {
        super(category, toLowerCase(values));
    }

    @Override
    public LookupResult<List<String>> lookupActualValues(RequestContext context) {
        return LookupResult.of(
                context.lookupStrings(category).getValues().stream()
                        .map(IntersectsStrings::toLowerCase)
                        .collect(Collectors.toList()));
    }

    private static List<String> toLowerCase(List<String> values) {
        return values.stream().map(String::toLowerCase).collect(Collectors.toList());
    }
}
