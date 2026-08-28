package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.ExtUserOptable;
import org.prebid.server.json.ObjectMapperProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtUserOptableResolverTest {

    @Test
    public void shouldResolveExtUserOptableWhenNodeIsValid() {
        // given
        final ObjectNode node = ObjectMapperProvider.mapper().createObjectNode();
        node.set("email", TextNode.valueOf("user@example.com"));
        node.set("phone", TextNode.valueOf("123"));
        node.set("zip", TextNode.valueOf("321"));
        node.set("vid", TextNode.valueOf("vid"));
        node.set("id5_signature", TextNode.valueOf("signature"));

        // when
        final ExtUserOptable result = ExtUserOptableResolver.resolveExtUserOptable(node, 1.0);

        // then
        assertThat(result).isNotNull()
                .returns("user@example.com", ExtUserOptable::getEmail)
                .returns("123", ExtUserOptable::getPhone)
                .returns("321", ExtUserOptable::getZip)
                .returns("vid", ExtUserOptable::getVid)
                .returns("signature", ExtUserOptable::getId5Signature);
    }

    @Test
    public void shouldReturnNullWhenNodeIsNotParseable() {
        // given
        final ObjectNode node = ObjectMapperProvider.mapper().createObjectNode();
        node.set("email", ObjectMapperProvider.mapper().createArrayNode().add(1).add(2));

        // when
        final ExtUserOptable result = ExtUserOptableResolver.resolveExtUserOptable(node, 1.0);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void shouldResolveExtUserOptableWhenNodeIsEmpty() {
        // given
        final ObjectNode node = ObjectMapperProvider.mapper().createObjectNode();

        // when
        final ExtUserOptable result = ExtUserOptableResolver.resolveExtUserOptable(node, 1.0);

        // then
        assertThat(result).isNotNull()
                .returns(null, ExtUserOptable::getEmail)
                .returns(null, ExtUserOptable::getPhone)
                .returns(null, ExtUserOptable::getZip)
                .returns(null, ExtUserOptable::getVid)
                .returns(null, ExtUserOptable::getId5Signature);
    }
}
