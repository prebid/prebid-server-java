package org.prebid.server.json.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.prebid.server.settings.model.activity.rule.AccountActivityRuleConfig;
import org.prebid.server.settings.model.activity.rule.AccountActivityRuleConfigResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AccountActivityRulesConfigDeserializer extends StdDeserializer<List<? extends AccountActivityRuleConfig>> {

    protected AccountActivityRulesConfigDeserializer() {
        super(List.class);
    }

    @Override
    public List<? extends AccountActivityRuleConfig> deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {

        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        }

        if (parser.currentToken() != JsonToken.START_ARRAY) {
            reportWrongTokenException(parser, context);
        }

        final ObjectCodec codec = parser.getCodec();
        final ArrayNode node = codec.readTree(parser);

        final List<AccountActivityRuleConfig> rulesConfig = new ArrayList<>();
        for (JsonNode ruleNode : node) {
            final Class<? extends AccountActivityRuleConfig> type = AccountActivityRuleConfigResolver.resolve(ruleNode);
            rulesConfig.add(codec.treeToValue(ruleNode, type));
        }

        return Collections.unmodifiableList(rulesConfig);
    }

    private static void reportWrongTokenException(JsonParser parser, DeserializationContext context)
            throws IOException {

        context.reportWrongTokenException(
                JsonToken.class,
                JsonToken.START_ARRAY,
                "Failed to parse field %s to array with a reason: Expected array.".formatted(parser.getCurrentName()));
    }
}
