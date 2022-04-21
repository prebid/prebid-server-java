package org.prebid.server.deals.targeting.interpret;

import lombok.EqualsAndHashCode;
import org.prebid.server.deals.targeting.RequestContext;
import org.prebid.server.deals.targeting.model.LookupResult;
import org.prebid.server.deals.targeting.syntax.TargetingCategory;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
public class InIntegers extends In<Integer> {

    public InIntegers(TargetingCategory category, List<Integer> values) {
        super(category, values);
    }

    @Override
    public LookupResult<Integer> lookupActualValue(RequestContext context) {
        return context.lookupInteger(category);
    }
}
