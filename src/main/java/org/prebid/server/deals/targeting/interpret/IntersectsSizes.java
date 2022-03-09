package org.prebid.server.deals.targeting.interpret;

import lombok.EqualsAndHashCode;
import org.prebid.server.deals.targeting.RequestContext;
import org.prebid.server.deals.targeting.model.LookupResult;
import org.prebid.server.deals.targeting.model.Size;
import org.prebid.server.deals.targeting.syntax.TargetingCategory;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
public class IntersectsSizes extends Intersects<Size> {

    public IntersectsSizes(TargetingCategory category, List<Size> values) {
        super(category, values);
    }

    @Override
    public LookupResult<List<Size>> lookupActualValues(RequestContext context) {
        return context.lookupSizes(category);
    }
}
