package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;

import java.util.Optional;

public class ChannelFunction implements SchemaFunction<RequestContext> {

    public static final String NAME = "channel";

    @Override
    public String extract(SchemaFunctionArguments<RequestContext> arguments) {
        return Optional.of(arguments.getOperand().getBidRequest())
                .map(BidRequest::getExt)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getChannel)
                .map(ExtRequestPrebidChannel::getName)
                .map(ChannelFunction::resolveChannel)
                .orElse(SchemaFunction.UNDEFINED_RESULT);
    }

    private static String resolveChannel(String channel) {
        return channel.equals("pbjs") ? "web" : channel;
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertNoArgs(config);
    }
}
