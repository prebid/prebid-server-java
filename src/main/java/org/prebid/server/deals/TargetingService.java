package org.prebid.server.deals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.iab.openrtb.request.Imp;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.deals.targeting.RequestContext;
import org.prebid.server.deals.targeting.TargetingDefinition;
import org.prebid.server.deals.targeting.interpret.And;
import org.prebid.server.deals.targeting.interpret.DomainMetricAwareExpression;
import org.prebid.server.deals.targeting.interpret.Expression;
import org.prebid.server.deals.targeting.interpret.InIntegers;
import org.prebid.server.deals.targeting.interpret.InStrings;
import org.prebid.server.deals.targeting.interpret.IntersectsIntegers;
import org.prebid.server.deals.targeting.interpret.IntersectsSizes;
import org.prebid.server.deals.targeting.interpret.IntersectsStrings;
import org.prebid.server.deals.targeting.interpret.Matches;
import org.prebid.server.deals.targeting.interpret.Not;
import org.prebid.server.deals.targeting.interpret.Or;
import org.prebid.server.deals.targeting.interpret.Within;
import org.prebid.server.deals.targeting.model.GeoRegion;
import org.prebid.server.deals.targeting.model.Size;
import org.prebid.server.deals.targeting.syntax.BooleanOperator;
import org.prebid.server.deals.targeting.syntax.MatchingFunction;
import org.prebid.server.deals.targeting.syntax.TargetingCategory;
import org.prebid.server.exception.TargetingSyntaxException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.util.StreamUtil;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Responsible for parsing and interpreting targeting defined in the Line Items’ metadata
 * and determining if individual requests match those targeting conditions.
 */
public class TargetingService {

    private final JacksonMapper mapper;

    public TargetingService(JacksonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    /**
     * Accepts targeting definition expressed in JSON syntax (see below),
     * parses it and transforms it into an object supporting efficient evaluation
     * of the targeting rules against the OpenRTB2 request.
     */
    public TargetingDefinition parseTargetingDefinition(JsonNode targetingDefinition, String lineItemId) {
        return TargetingDefinition.of(parseNode(targetingDefinition, lineItemId));
    }

    /**
     * Accepts OpenRTB2 request and particular Imp object to evaluate Line Item targeting
     * definition against and returns whether it is matched or not.
     */
    public boolean matchesTargeting(AuctionContext auctionContext, Imp imp, TargetingDefinition targetingDefinition) {
        final RequestContext requestContext = new RequestContext(
                auctionContext.getBidRequest(), imp, auctionContext.getTxnLog(), mapper);

        return targetingDefinition.getRootExpression().matches(requestContext);
    }

    private Expression parseNode(JsonNode node, String lineItemId) {
        final Map.Entry<String, JsonNode> field = validateIsSingleElementObject(node);
        final String fieldName = field.getKey();

        if (BooleanOperator.isBooleanOperator(fieldName)) {
            return parseBooleanOperator(fieldName, field.getValue(), lineItemId);
        } else if (TargetingCategory.isTargetingCategory(fieldName)) {
            return parseTargetingCategory(fieldName, field.getValue(), lineItemId);
        } else {
            throw new TargetingSyntaxException(
                    String.format("Expected either boolean operator or targeting category, got %s", fieldName));
        }
    }

    private Expression parseBooleanOperator(String fieldName, JsonNode value, String lineItemId) {
        final BooleanOperator operator = BooleanOperator.fromString(fieldName);
        switch (operator) {
            case AND:
                return new And(parseArray(value, node -> parseNode(node, lineItemId)));
            case OR:
                return new Or(parseArray(value, node -> parseNode(node, lineItemId)));
            case NOT:
                return new Not(parseNode(value, lineItemId));
            default:
                throw new IllegalStateException(String.format("Unexpected boolean operator %s", operator));
        }
    }

    private Expression parseTargetingCategory(String fieldName, JsonNode value, String lineItemId) {
        final TargetingCategory category = TargetingCategory.fromString(fieldName);
        switch (category.type()) {
            case size:
                return new IntersectsSizes(category,
                        parseArrayFunction(value, MatchingFunction.INTERSECTS, this::parseSize));
            case mediaType:
            case userSegment:
                return new IntersectsStrings(category,
                        parseArrayFunction(value, MatchingFunction.INTERSECTS, TargetingService::parseString));
            case domain:
                return new DomainMetricAwareExpression(parseStringFunction(category, value), lineItemId);
            case referrer:
            case appBundle:
            case adslot:
                return parseStringFunction(category, value);
            case pagePosition:
            case dow:
            case hour:
                return new InIntegers(category,
                        parseArrayFunction(value, MatchingFunction.IN, TargetingService::parseInteger));
            case deviceGeoExt:
            case deviceExt:
                return new InStrings(category,
                        parseArrayFunction(value, MatchingFunction.IN, TargetingService::parseString));
            case location:
                return new Within(category, parseSingleObjectFunction(value, MatchingFunction.WITHIN,
                        this::parseGeoRegion));
            case bidderParam:
            case userFirstPartyData:
            case siteFirstPartyData:
                return parseTypedFunction(category, value);
            default:
                throw new IllegalStateException(String.format("Unexpected targeting category type %s", category));
        }
    }

    private static <T> List<T> parseArrayFunction(JsonNode value, MatchingFunction function,
                                                  Function<JsonNode, T> mapper) {

        return parseArray(validateIsFunction(value, function), mapper);
    }

    private static <T> T parseSingleObjectFunction(
            JsonNode value, MatchingFunction function, Function<JsonNode, T> mapper) {

        return mapper.apply(validateIsFunction(value, function));
    }

    private static Expression parseStringFunction(TargetingCategory category, JsonNode value) {
        final Map.Entry<String, JsonNode> field = validateIsSingleElementObject(value);
        final MatchingFunction function =
                validateCompatibleFunction(field, MatchingFunction.MATCHES, MatchingFunction.IN);

        switch (function) {
            case MATCHES:
                return new Matches(category, parseString(field.getValue()));
            case IN:
                return createInStringsFunction(category, field.getValue());
            default:
                throw new IllegalStateException(String.format("Unexpected string function %s", function.value()));
        }
    }

    private static Expression parseTypedFunction(TargetingCategory category, JsonNode value) {
        final Map.Entry<String, JsonNode> field = validateIsSingleElementObject(value);
        final MatchingFunction function = validateCompatibleFunction(field,
                MatchingFunction.MATCHES, MatchingFunction.IN, MatchingFunction.INTERSECTS);

        final JsonNode functionValue = field.getValue();
        switch (function) {
            case MATCHES:
                return new Matches(category, parseString(functionValue));
            case IN:
                return parseTypedInFunction(category, functionValue);
            case INTERSECTS:
                return parseTypedIntersectsFunction(category, functionValue);
            default:
                throw new IllegalStateException(String.format("Unexpected typed function %s", function.value()));
        }
    }

    private Size parseSize(JsonNode node) {
        validateIsObject(node);

        final Size size;
        try {
            size = mapper.mapper().treeToValue(node, Size.class);
        } catch (JsonProcessingException e) {
            throw new TargetingSyntaxException(
                    String.format("Exception occurred while parsing size: %s", e.getMessage()), e);
        }

        if (size.getH() == null || size.getW() == null) {
            throw new TargetingSyntaxException("Height and width in size definition could not be null or missing");
        }

        return size;
    }

    private static String parseString(JsonNode node) {
        validateIsString(node);

        final String value = node.textValue();
        if (StringUtils.isEmpty(value)) {
            throw new TargetingSyntaxException("String value could not be empty");
        }
        return value;
    }

    private static Integer parseInteger(JsonNode node) {
        validateIsInteger(node);

        return node.intValue();
    }

    private GeoRegion parseGeoRegion(JsonNode node) {
        validateIsObject(node);

        final GeoRegion region;
        try {
            region = mapper.mapper().treeToValue(node, GeoRegion.class);
        } catch (JsonProcessingException e) {
            throw new TargetingSyntaxException(
                    String.format("Exception occurred while parsing geo region: %s", e.getMessage()), e);
        }

        if (region.getLat() == null || region.getLon() == null || region.getRadiusMiles() == null) {
            throw new TargetingSyntaxException(
                    "Lat, lon and radiusMiles in geo region definition could not be null or missing");
        }

        return region;
    }

    private static <T> List<T> parseArray(JsonNode node, Function<JsonNode, T> mapper) {
        validateIsArray(node);

        return StreamUtil.asStream(node.spliterator()).map(mapper).collect(Collectors.toList());
    }

    private static Expression parseTypedInFunction(TargetingCategory category, JsonNode value) {
        return parseTypedArrayFunction(category, value, TargetingService::createInIntegersFunction,
                TargetingService::createInStringsFunction);
    }

    private static Expression parseTypedIntersectsFunction(TargetingCategory category, JsonNode value) {
        return parseTypedArrayFunction(category, value, TargetingService::createIntersectsIntegersFunction,
                TargetingService::createIntersectsStringsFunction);
    }

    private static Expression parseTypedArrayFunction(
            TargetingCategory category, JsonNode value,
            BiFunction<TargetingCategory, JsonNode, Expression> integerCreator,
            BiFunction<TargetingCategory, JsonNode, Expression> stringCreator) {

        validateIsArray(value);

        final Iterator<JsonNode> iterator = value.iterator();

        final JsonNodeType dataType = iterator.hasNext() ? iterator.next().getNodeType() : JsonNodeType.STRING;
        switch (dataType) {
            case NUMBER:
                return integerCreator.apply(category, value);
            case STRING:
                return stringCreator.apply(category, value);
            default:
                throw new TargetingSyntaxException(String.format("Expected integer or string, got %s", dataType));
        }
    }

    private static Expression createInIntegersFunction(TargetingCategory category, JsonNode value) {
        return new InIntegers(category, parseArray(value, TargetingService::parseInteger));
    }

    private static InStrings createInStringsFunction(TargetingCategory category, JsonNode value) {
        return new InStrings(category, parseArray(value, TargetingService::parseString));
    }

    private static Expression createIntersectsStringsFunction(TargetingCategory category, JsonNode value) {
        return new IntersectsStrings(category, parseArray(value, TargetingService::parseString));
    }

    private static Expression createIntersectsIntegersFunction(TargetingCategory category, JsonNode value) {
        return new IntersectsIntegers(category, parseArray(value, TargetingService::parseInteger));
    }

    private static void validateIsObject(JsonNode value) {
        if (!value.isObject()) {
            throw new TargetingSyntaxException(String.format("Expected object, got %s", value.getNodeType()));
        }
    }

    private static Map.Entry<String, JsonNode> validateIsSingleElementObject(JsonNode value) {
        validateIsObject(value);

        if (value.size() != 1) {
            throw new TargetingSyntaxException(
                    String.format("Expected only one element in the object, got %d", value.size()));
        }

        return value.fields().next();
    }

    private static void validateIsArray(JsonNode value) {
        if (!value.isArray()) {
            throw new TargetingSyntaxException(String.format("Expected array, got %s", value.getNodeType()));
        }
    }

    private static void validateIsString(JsonNode value) {
        if (!value.isTextual()) {
            throw new TargetingSyntaxException(String.format("Expected string, got %s", value.getNodeType()));
        }
    }

    private static void validateIsInteger(JsonNode value) {
        if (!value.isInt()) {
            throw new TargetingSyntaxException(String.format("Expected integer, got %s", value.getNodeType()));
        }
    }

    private static JsonNode validateIsFunction(JsonNode value, MatchingFunction function) {
        final Map.Entry<String, JsonNode> field = validateIsSingleElementObject(value);
        final String fieldName = field.getKey();

        if (!MatchingFunction.isMatchingFunction(fieldName)) {
            throw new TargetingSyntaxException(String.format("Expected matching function, got %s", fieldName));
        } else if (MatchingFunction.fromString(fieldName) != function) {
            throw new TargetingSyntaxException(
                    String.format("Expected %s matching function, got %s", function.value(), fieldName));
        }

        return field.getValue();
    }

    private static MatchingFunction validateCompatibleFunction(Map.Entry<String, JsonNode> field,
                                                               MatchingFunction... compatibleFunctions) {
        final String fieldName = field.getKey();

        if (!MatchingFunction.isMatchingFunction(fieldName)) {
            throw new TargetingSyntaxException(String.format("Expected matching function, got %s", fieldName));
        }

        final MatchingFunction function = MatchingFunction.fromString(fieldName);
        if (!Arrays.asList(compatibleFunctions).contains(function)) {
            throw new TargetingSyntaxException(String.format("Expected one of %s matching functions, got %s",
                    Arrays.stream(compatibleFunctions).map(MatchingFunction::value).collect(Collectors.joining(", ")),
                    fieldName));
        }
        return function;
    }
}
