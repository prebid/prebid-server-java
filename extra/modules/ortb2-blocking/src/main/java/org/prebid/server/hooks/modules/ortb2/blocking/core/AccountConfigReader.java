package org.prebid.server.hooks.modules.ortb2.blocking.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.hooks.modules.ortb2.blocking.core.exception.InvalidAccountConfigurationException;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.BidAttributeBlockingConfig;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.BlockedAttributes;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.ResponseBlockingConfig;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.Result;
import org.prebid.server.hooks.modules.ortb2.blocking.core.util.MergeUtils;
import org.prebid.server.util.StreamUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AccountConfigReader {

    private static final String ATTRIBUTES_FIELD = "attributes";
    private static final String BADV_FIELD = "badv";
    private static final String BCAT_FIELD = "bcat";
    private static final String BAPP_FIELD = "bapp";
    private static final String BTYPE_FIELD = "btype";
    private static final String BATTR_FIELD = "battr";
    private static final String ENFORCE_BLOCKS_FIELD = "enforce-blocks";
    private static final String BLOCK_UNKNOWN_ADOMAIN_FIELD = "block-unknown-adomain";
    private static final String BLOCKED_ADOMAIN_FIELD = "blocked-adomain";
    private static final String ALLOWED_ADOMAIN_FOR_DEALS_FIELD = "allowed-adomain-for-deals";
    private static final String BLOCKED_ADV_CAT_FIELD = "blocked-adv-cat";
    private static final String BLOCK_UNKNOWN_ADV_CAT_FIELD = "block-unknown-adv-cat";
    private static final String ALLOWED_ADV_CAT_FOR_DEALS_FIELD = "allowed-adv-cat-for-deals";
    private static final String BLOCKED_APP_FIELD = "blocked-app";
    private static final String ALLOWED_APP_FOR_DEALS_FIELD = "allowed-app-for-deals";
    private static final String BLOCKED_BANNER_TYPE_FIELD = "blocked-banner-type";
    private static final String BLOCKED_BANNER_ATTR_FIELD = "blocked-banner-attr";
    private static final String ALLOWED_BANNER_ATTR_FOR_DEALS = "allowed-banner-attr-for-deals";
    private static final String ACTION_OVERRIDES_FIELD = "action-overrides";
    private static final String OVERRIDE_FIELD = "override";
    private static final String CONDITIONS_FIELD = "conditions";
    private static final String BIDDERS_FIELD = "bidders";
    private static final String MEDIA_TYPE_FIELD = "media-type";
    private static final String DEALIDS_FIELD = "deal-ids";

    private static final String AUDIO_MEDIA_TYPE = "audio";
    private static final String VIDEO_MEDIA_TYPE = "video";
    private static final String BANNER_MEDIA_TYPE = "banner";
    private static final String NATIVE_MEDIA_TYPE = "native";

    private final ObjectNode config;
    private final String bidder;
    private final boolean debugEnabled;

    private AccountConfigReader(ObjectNode config, String bidder, boolean debugEnabled) {
        this.config = config;
        this.bidder = bidder;
        this.debugEnabled = debugEnabled;
    }

    public static AccountConfigReader create(ObjectNode config, String bidder, boolean debugEnabled) {
        return new AccountConfigReader(config, bidder, debugEnabled);
    }

    public Result<BlockedAttributes> blockedAttributesFor(BidRequest bidRequest) {
        if (attributes() == null) {
            return Result.empty();
        }

        final Set<String> requestMediaTypes = mediaTypesFrom(bidRequest);

        final Result<List<String>> badv =
            blockedAttribute(BADV_FIELD, String.class, BLOCKED_ADOMAIN_FIELD, requestMediaTypes);
        final Result<List<String>> bcat =
            blockedAttribute(BCAT_FIELD, String.class, BLOCKED_ADV_CAT_FIELD, requestMediaTypes);
        final Result<List<String>> bapp =
            blockedAttribute(BAPP_FIELD, String.class, BLOCKED_APP_FIELD, requestMediaTypes);
        final Result<Map<String, List<Integer>>> btype =
            blockedAttributesForImps(BTYPE_FIELD, Integer.class, BLOCKED_BANNER_TYPE_FIELD, bidRequest);
        final Result<Map<String, List<Integer>>> battr =
            blockedAttributesForImps(BATTR_FIELD, Integer.class, BLOCKED_BANNER_ATTR_FIELD, bidRequest);

        return Result.of(
            toBlockedAttributes(badv, bcat, bapp, btype, battr),
            MergeUtils.mergeMessages(badv, bcat, bapp, btype, battr));
    }

    public Result<ResponseBlockingConfig> responseBlockingConfigFor(BidderBid bidderBid) {
        final Set<String> bidMediaTypes = mediaTypesFrom(bidderBid);
        final String dealid = bidderBid.getBid().getDealid();

        final Result<BidAttributeBlockingConfig<String>> badv = blockingConfigForAttribute(
            BADV_FIELD,
            String.class,
            BLOCK_UNKNOWN_ADOMAIN_FIELD,
            ALLOWED_ADOMAIN_FOR_DEALS_FIELD,
            bidMediaTypes,
            dealid);
        final Result<BidAttributeBlockingConfig<String>> bcat = blockingConfigForAttribute(
            BCAT_FIELD,
            String.class,
            BLOCK_UNKNOWN_ADV_CAT_FIELD,
            ALLOWED_ADV_CAT_FOR_DEALS_FIELD,
            bidMediaTypes,
            dealid);
        final Result<BidAttributeBlockingConfig<String>> bapp = blockingConfigForAttribute(
            BAPP_FIELD,
            String.class,
            ALLOWED_APP_FOR_DEALS_FIELD,
            bidMediaTypes,
            dealid);
        final Result<BidAttributeBlockingConfig<Integer>> battr = blockingConfigForAttribute(
            BATTR_FIELD,
            Integer.class,
            ALLOWED_BANNER_ATTR_FOR_DEALS,
            bidMediaTypes,
            dealid);

        final ResponseBlockingConfig response = ResponseBlockingConfig.builder()
            .badv(badv.getValue())
            .bcat(bcat.getValue())
            .bapp(bapp.getValue())
            .battr(battr.getValue())
            .build();

        final List<String> warnings = MergeUtils.mergeMessages(badv, bcat, bapp, battr);

        return Result.of(response, warnings);
    }

    private <T> Result<List<T>> blockedAttribute(
        String attribute, Class<T> attributeType, String fieldName, Set<String> actualMediaTypes) {

        final JsonNode attributeConfig = attributeConfig(attribute);
        if (attributeConfig == null) {
            return Result.empty();
        }

        final Result<JsonNode> override =
            overrideFor(attributeConfig, actualMediaTypes, fieldName);

        final List<T> result = mergeTypedArrays(attributeConfig, override.getValue(), attributeType, fieldName);

        return Result.of(result, override.getMessages());
    }

    private <T> Result<Map<String, List<T>>> blockedAttributesForImps(
        String attribute, Class<T> attributeType, String fieldName, BidRequest bidRequest) {

        final Map<String, List<T>> attributeValues = new HashMap<>();
        final List<Result<?>> results = new ArrayList<>();

        for (final Imp imp : bidRequest.getImp()) {
            final Result<List<T>> attributeForImp =
                blockedAttribute(attribute, attributeType, fieldName, mediaTypesFrom(imp));

            if (attributeForImp.hasValue()) {
                attributeValues.put(imp.getId(), attributeForImp.getValue());
            }
            results.add(attributeForImp);
        }

        return Result.of(
            !attributeValues.isEmpty() ? attributeValues : null,
            MergeUtils.mergeMessages(results));
    }

    private <T> Result<BidAttributeBlockingConfig<T>> blockingConfigForAttribute(
        String attribute,
        Class<T> attributeType,
        String blockUnknownField,
        String allowedForDealsField,
        Set<String> bidMediaTypes,
        String dealid) {

        final JsonNode attributeConfig = attributeConfig(attribute);
        if (attributeConfig == null) {
            return Result.empty();
        }

        final Result<JsonNode> enforceBlocksOverrideResult =
            overrideFor(attributeConfig, bidMediaTypes, ENFORCE_BLOCKS_FIELD);
        final boolean enforceBlocks =
            mergeBoolean(attributeConfig, enforceBlocksOverrideResult.getValue(), ENFORCE_BLOCKS_FIELD);

        // for attributes that don't support blocking bids with unknown values
        final Result<JsonNode> blockUnknownOverrideResult = blockUnknownField != null
            ? overrideFor(attributeConfig, bidMediaTypes, blockUnknownField)
            : Result.empty();
        final boolean blockUnknown = blockUnknownField != null
            && mergeBoolean(attributeConfig, blockUnknownOverrideResult.getValue(), blockUnknownField);

        final Set<T> dealExceptions =
            StringUtils.isNotBlank(dealid)
                ? mergeDealExceptions(
                attributeConfig,
                dealExceptionsFor(attributeConfig, dealid, allowedForDealsField),
                attributeType,
                allowedForDealsField)
                : Collections.emptySet();

        final BidAttributeBlockingConfig<T> blockingConfig =
            BidAttributeBlockingConfig.of(enforceBlocks, blockUnknown, dealExceptions);
        final List<String> warnings = MergeUtils.mergeMessages(enforceBlocksOverrideResult, blockUnknownOverrideResult);

        return Result.of(blockingConfig, warnings);
    }

    private <T> Result<BidAttributeBlockingConfig<T>> blockingConfigForAttribute(
        String attribute,
        Class<T> type,
        String allowedForDealsField,
        Set<String> bidMediaTypes,
        String dealid) {

        return blockingConfigForAttribute(attribute, type, null, allowedForDealsField, bidMediaTypes, dealid);
    }

    private JsonNode attributes() {
        return config != null ? objectNodeFrom(config, ATTRIBUTES_FIELD) : null;
    }

    private JsonNode attributeConfig(String attribute) {
        final JsonNode attributes = attributes();

        return attributes != null ? objectNodeFrom(attributes, attribute) : null;

    }

    private static Set<String> mediaTypesFrom(BidRequest bidRequest) {
        return bidRequest.getImp().stream()
            .flatMap(imp -> mediaTypesFrom(imp).stream())
            .collect(Collectors.toSet());
    }

    private static Set<String> mediaTypesFrom(Imp imp) {
        final Set<String> mediaTypes = new HashSet<>();

        if (imp.getAudio() != null) {
            mediaTypes.add(AUDIO_MEDIA_TYPE);
        }
        if (imp.getVideo() != null) {
            mediaTypes.add(VIDEO_MEDIA_TYPE);
        }
        if (imp.getBanner() != null) {
            mediaTypes.add(BANNER_MEDIA_TYPE);
        }
        if (imp.getXNative() != null) {
            mediaTypes.add(NATIVE_MEDIA_TYPE);
        }

        return mediaTypes;
    }

    private static Set<String> mediaTypesFrom(BidderBid bidderBid) {
        return Collections.singleton(bidderBid.getType().getName());
    }

    private Result<JsonNode> overrideFor(JsonNode parent, Set<String> actualMediaTypes, String field) {
        final JsonNode actionOverrides = objectNodeFrom(parent, ACTION_OVERRIDES_FIELD);
        final JsonNode overridesForField = actionOverrides != null ? objectArrayFrom(actionOverrides, field) : null;
        if (overridesForField == null) {
            return Result.empty();
        }

        final List<JsonNode> specificBidderResults = new ArrayList<>();
        final List<JsonNode> catchAllBidderResults = new ArrayList<>();

        for (final JsonNode override : overridesForField) {
            final JsonNode conditions = requireNonNull(
                objectNodeFrom(override, CONDITIONS_FIELD), CONDITIONS_FIELD);
            final List<String> bidders = typedArrayFrom(conditions, String.class, BIDDERS_FIELD);
            final List<String> mediaTypes = typedArrayFrom(conditions, String.class, MEDIA_TYPE_FIELD);

            if (bidders == null && mediaTypes == null) {
                throw new InvalidAccountConfigurationException(
                    String.format("%s field in account configuration must contain at least one of %s or %s",
                        CONDITIONS_FIELD,
                        BIDDERS_FIELD,
                        MEDIA_TYPE_FIELD));
            }

            final boolean catchAllBidders = bidders == null;
            final boolean matchesBidder = catchAllBidders || bidders.contains(bidder);
            final boolean matchesMediaTypes =
                mediaTypes == null || !Collections.disjoint(mediaTypes, actualMediaTypes);

            if (matchesBidder && matchesMediaTypes) {
                final JsonNode actions = requireNonNull(override.get(OVERRIDE_FIELD), OVERRIDE_FIELD);

                final List<JsonNode> results = catchAllBidders ? catchAllBidderResults : specificBidderResults;
                results.add(actions);
            }
        }

        return toResult(specificBidderResults, catchAllBidderResults, actualMediaTypes);
    }

    private Result<JsonNode> toResult(
        List<JsonNode> specificBidderResults,
        List<JsonNode> catchAllBidderResults,
        Set<String> actualMediaTypes) {

        final JsonNode value = ObjectUtils.firstNonNull(
            specificBidderResults.size() > 0 ? specificBidderResults.get(0) : null,
            catchAllBidderResults.size() > 0 ? catchAllBidderResults.get(0) : null);
        final List<String> warnings = debugEnabled && specificBidderResults.size() + catchAllBidderResults.size() > 1
            ? Collections.singletonList(String.format(
            "More than one conditions matches request. Bidder: %s, request media types: %s",
            bidder,
            actualMediaTypes))
            : null;

        return Result.of(value, warnings);
    }

    private static BlockedAttributes toBlockedAttributes(
        Result<List<String>> badv,
        Result<List<String>> bcat,
        Result<List<String>> bapp,
        Result<Map<String, List<Integer>>> btype,
        Result<Map<String, List<Integer>>> battr) {

        return badv.hasValue() || bcat.hasValue() || bapp.hasValue() || btype.hasValue() || battr.hasValue()
            ? BlockedAttributes.builder()
            .badv(badv.getValue())
            .bcat(bcat.getValue())
            .bapp(bapp.getValue())
            .btype(btype.getValue())
            .battr(battr.getValue())
            .build()
            : null;
    }

    private static List<JsonNode> dealExceptionsFor(JsonNode parent, String dealid, String field) {
        final JsonNode actionOverrides = objectNodeFrom(parent, ACTION_OVERRIDES_FIELD);
        final JsonNode overridesForField = actionOverrides != null ? objectArrayFrom(actionOverrides, field) : null;
        if (overridesForField == null) {
            return Collections.emptyList();
        }

        final List<JsonNode> results = new ArrayList<>();
        for (final JsonNode override : overridesForField) {
            final JsonNode conditions = requireNonNull(objectNodeFrom(override, CONDITIONS_FIELD), CONDITIONS_FIELD);
            final List<String> dealIds = typedArrayFrom(conditions, String.class, DEALIDS_FIELD);

            if (dealIds == null) {
                throw new InvalidAccountConfigurationException(String.format(
                    "%s field in account configuration must contain %s",
                    CONDITIONS_FIELD,
                    DEALIDS_FIELD));
            }

            if (dealIds.contains(dealid)) {
                results.add(requireNonNull(override.get(OVERRIDE_FIELD), OVERRIDE_FIELD));
            }
        }

        return results;
    }

    private static <T> List<T> mergeTypedArrays(JsonNode parent, JsonNode override, Class<T> type, String field) {
        return MergeUtils.merge(
            typedArrayFrom(parent, type, field),
            override != null ? asTypedArray(override, type, OVERRIDE_FIELD) : null);
    }

    private static boolean mergeBoolean(JsonNode parent, JsonNode override, String field) {
        return BooleanUtils.toBooleanDefaultIfNull(
            override != null ? typedAs(override, Boolean.class, OVERRIDE_FIELD) : null,
            BooleanUtils.toBooleanDefaultIfNull(typedFieldFrom(parent, Boolean.class, field), false));
    }

    private static <T> Set<T> mergeDealExceptions(
        JsonNode parent, List<JsonNode> overrides, Class<T> type, String field) {

        final List<T> defaultValue = typedArrayFrom(parent, type, field);
        if (defaultValue == null && CollectionUtils.isEmpty(overrides)) {
            return Collections.emptySet();
        }

        final Set<T> results = new HashSet<>(CollectionUtils.emptyIfNull(defaultValue));
        for (final JsonNode override : IterableUtils.emptyIfNull(overrides)) {
            results.addAll(asTypedArray(override, type, field));
        }

        return results;
    }

    private static <T> List<T> typedArrayFrom(JsonNode parent, Class<T> type, String field) {
        final JsonNode child = parent.get(field);
        if (child == null) {
            return null;
        }

        return asTypedArray(child, type, field);
    }

    private static <T> List<T> asTypedArray(JsonNode node, Class<T> type, String field) {
        if (node == null) {
            return null;
        }

        if (!node.isArray()) {
            throw new InvalidAccountConfigurationException(
                String.format("%s field in account configuration is not an array", field));
        }

        return StreamUtil.asStream(node.elements())
            .map(element -> typedAs(element, type, field))
            .collect(Collectors.toList());
    }

    private static <T> T typedFieldFrom(JsonNode parent, Class<T> type, String field) {
        final JsonNode child = parent.get(field);
        if (child == null) {
            return null;
        }

        return typedAs(child, type, field);
    }

    @SuppressWarnings("unchecked")
    private static <T> T typedAs(JsonNode node, Class<T> type, String field) {
        final Function<JsonNode, Boolean> checker;
        final Function<JsonNode, ?> converter;

        if (type.isAssignableFrom(String.class)) {
            checker = JsonNode::isTextual;
            converter = JsonNode::textValue;
        } else if (type.isAssignableFrom(Integer.class)) {
            checker = JsonNode::isInt;
            converter = JsonNode::intValue;
        } else if (type.isAssignableFrom(Boolean.class)) {
            checker = JsonNode::isBoolean;
            converter = JsonNode::booleanValue;
        } else {
            throw new IllegalArgumentException(String.format("Unsupported type: %s", type));
        }

        final Boolean hasDesiredType = checker.apply(node);
        if (!hasDesiredType) {
            throw new InvalidAccountConfigurationException(
                String.format("%s field in account configuration has unexpected type. Expected %s", field, type));
        }

        return (T) converter.apply(node);
    }

    private static JsonNode objectNodeFrom(JsonNode parent, String field) {
        final JsonNode child = parent.get(field);
        if (child == null) {
            return null;
        }

        if (!child.isObject()) {
            throw new InvalidAccountConfigurationException(
                String.format("%s field in account configuration is not an object", field));
        }

        return child;
    }

    private static JsonNode objectArrayFrom(JsonNode parent, String field) {
        final JsonNode child = parent.get(field);
        if (child == null) {
            return null;
        }

        if (!child.isArray() || !StreamUtil.asStream(child.elements()).allMatch(JsonNode::isObject)) {
            throw new InvalidAccountConfigurationException(
                String.format("%s field in account configuration is not an array of objects", field));
        }

        return child;
    }

    private static <T> T requireNonNull(T object, String field) {
        if (object == null) {
            throw new InvalidAccountConfigurationException(
                String.format("%s field in account configuration is missing", field));
        }

        return object;
    }
}
