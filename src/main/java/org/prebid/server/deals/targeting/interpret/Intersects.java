package org.prebid.server.deals.targeting.interpret;

import lombok.EqualsAndHashCode;
import org.prebid.server.deals.targeting.RequestContext;
import org.prebid.server.deals.targeting.syntax.TargetingCategory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@EqualsAndHashCode
public abstract class Intersects<T> implements TerminalExpression {

    protected final TargetingCategory category;

    protected List<T> values;

    public Intersects(TargetingCategory category, List<T> values) {
        this.category = Objects.requireNonNull(category);
        this.values = Collections.unmodifiableList(values);
    }

    @Override
    public boolean matches(RequestContext context) {
        return !Collections.disjoint(values, lookupActualValues(context));
    }

    protected abstract List<T> lookupActualValues(RequestContext context);
}
