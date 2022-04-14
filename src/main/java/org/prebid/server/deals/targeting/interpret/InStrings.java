package org.prebid.server.deals.targeting.interpret;

import lombok.EqualsAndHashCode;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.deals.targeting.RequestContext;
import org.prebid.server.deals.targeting.model.LookupResult;
import org.prebid.server.deals.targeting.syntax.TargetingCategory;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@EqualsAndHashCode(callSuper = true)
public class InStrings extends In<String> {

    public InStrings(TargetingCategory category, List<String> values) {
        super(category, toLowerCase(values));
    }

    @Override
    public LookupResult<String> lookupActualValue(RequestContext context) {
        final List<String> actualValue = firstNonEmpty(
                () -> context.lookupString(category).getValues(),
                () -> lookupIntegerAsString(context));

        return actualValue != null
                ? LookupResult.of(actualValue.stream().map(String::toLowerCase).collect(Collectors.toList()))
                : LookupResult.empty();
    }

    private List<String> lookupIntegerAsString(RequestContext context) {
        final List<Integer> actualValue = context.lookupInteger(category).getValues();
        return actualValue.stream().map(Object::toString).collect(Collectors.toList());
    }

    private static List<String> toLowerCase(List<String> values) {
        return values.stream().map(String::toLowerCase).collect(Collectors.toList());
    }

    @SafeVarargs
    private static List<String> firstNonEmpty(Supplier<List<String>>... suppliers) {
        return Stream.of(suppliers)
                .map(Supplier::get)
                .filter(CollectionUtils::isNotEmpty)
                .findFirst()
                .orElse(null);
    }
}
