package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.rule.engine.core.request.Granularity;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;
import org.prebid.server.spring.config.bidder.model.MediaType;
import org.prebid.server.util.StreamUtil;

import java.util.HashSet;
import java.util.Set;

public class MediaTypeInFunction implements SchemaFunction<BidRequest, RequestRuleContext> {

    public static final String NAME = "mediaTypeIn";

    private static final String TYPES_FIELD = "types";

    @Override
    public String extract(SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments) {
        final RequestRuleContext context = arguments.getContext();
        final String impId = ((Granularity.Imp) context.getGranularity()).impId();
        final BidRequest bidRequest = arguments.getOperand();

        final Imp adUnit = ListUtils.emptyIfNull(bidRequest.getImp()).stream()
                .filter(imp -> StringUtils.equals(imp.getId(), impId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Critical error in rules engine. Imp id of absent imp supplied"));

        final Set<String> adUnitMediaTypes = adUnitMediaTypes(adUnit);

        final boolean intersects = StreamUtil.asStream(arguments.getConfig().get(TYPES_FIELD).elements())
                .map(JsonNode::asText)
                .anyMatch(adUnitMediaTypes::contains);

        return Boolean.toString(intersects);
    }

    private static Set<String> adUnitMediaTypes(Imp imp) {
        final Set<String> result = new HashSet<>();

        if (imp.getBanner() != null) {
            result.add(MediaType.BANNER.getKey());
        }
        if (imp.getVideo() != null) {
            result.add(MediaType.VIDEO.getKey());
        }
        if (imp.getAudio() != null) {
            result.add(MediaType.AUDIO.getKey());
        }
        if (imp.getXNative() != null) {
            result.add(MediaType.NATIVE.getKey());
        }

        return result;
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertArrayOfStrings(config, TYPES_FIELD);
    }
}
