package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.spring.config.bidder.model.MediaType;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MediaTypeInFunction implements SchemaFunction<RequestContext> {

    public static final String NAME = "mediaTypeIn";

    @Override
    public String extract(SchemaFunctionArguments<RequestContext> arguments) {
        final RequestContext context = arguments.getOperand();

        final String impId = context.getImpId();
        final BidRequest bidRequest = context.getBidRequest();

        final Imp adUnit = ListUtils.emptyIfNull(bidRequest.getImp()).stream()
                .filter(imp -> StringUtils.equals(impId, impId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Critical error in rules engine. Imp id of absent imp supplied"));

        final Set<String> mediaTypesToMatch = parseMediaTypes(arguments.getConfig());
        final Set<String> adUnitMediaTypes = adUnitMediaTypes(adUnit);

        boolean intersects = !SetUtils.intersection(mediaTypesToMatch, adUnitMediaTypes).isEmpty();

        return Boolean.toString(intersects);
    }

    private Set<String> parseMediaTypes(ObjectNode config) {
        // to lower case
        return Collections.emptySet();
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
    public void validateConfigArguments(ObjectNode args) {
        SchemaFunction.super.validateConfigArguments(args);
    }
}
