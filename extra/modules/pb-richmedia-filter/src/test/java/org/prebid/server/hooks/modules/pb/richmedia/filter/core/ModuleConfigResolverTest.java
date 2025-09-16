package org.prebid.server.hooks.modules.pb.richmedia.filter.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.pb.richmedia.filter.model.PbRichMediaFilterProperties;
import org.prebid.server.json.ObjectMapperProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleConfigResolverTest {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperProvider.mapper();

    private static final PbRichMediaFilterProperties GLOBAL_PROPERTIES =
            PbRichMediaFilterProperties.of(false, "pattern");
    private static final PbRichMediaFilterProperties ACCOUNT_PROPERTIES =
            PbRichMediaFilterProperties.of(true, "<script src=\"mraid.js\"></script>");

    private ModuleConfigResolver target;

    @BeforeEach
    public void before() {
        target = new ModuleConfigResolver(OBJECT_MAPPER, GLOBAL_PROPERTIES);
    }

    @Test
    public void resolveShouldReturnAccountConfigWhenAccountConfigIsPresent() throws JsonProcessingException {
        // given
        final String accountConfig = """
                {
                   "filter-mraid": true,
                   "mraid-script-pattern": "<script src=\\"mraid.js\\"></script>"
                }
                """;
        final ObjectNode objectNode = OBJECT_MAPPER.readValue(accountConfig, ObjectNode.class);

        // when
        final PbRichMediaFilterProperties actualProperties = target.resolve(objectNode);

        // then
        assertThat(actualProperties).isEqualTo(ACCOUNT_PROPERTIES);
    }

    @Test
    public void resolveShouldReturnGlobalConfigWhenAccountConfigIsEmpty() {
        // given
        final ObjectNode emptyObjectNode = OBJECT_MAPPER.createObjectNode();

        // when
        final PbRichMediaFilterProperties actualProperties = target.resolve(emptyObjectNode);

        // then
        assertThat(actualProperties).isEqualTo(GLOBAL_PROPERTIES);
    }

    @Test
    public void resolveShouldReturnGlobalConfigWhenAccountConfigIsAbsent() {
        // when
        final PbRichMediaFilterProperties actualProperties = target.resolve(null);

        // then
        assertThat(actualProperties).isEqualTo(GLOBAL_PROPERTIES);
    }

    @Test
    public void resolveShouldReturnGlobalConfigWhenAccountConfigCanNotBeParsed() throws JsonProcessingException {
        // given
        final String invalidAccountConfig = """
                {
                    "filter-mraid": "invalid_type",
                    "mraid-script-pattern": "<script src=\\"mraid.js\\"></script>"
                }
                """;
        final ObjectNode objectNode = OBJECT_MAPPER.readValue(invalidAccountConfig, ObjectNode.class);

        // when
        final PbRichMediaFilterProperties actualProperties = target.resolve(objectNode);

        // then
        assertThat(actualProperties).isEqualTo(GLOBAL_PROPERTIES);
    }

    @Test
    public void resolveShouldReturnGlobalConfigWhenAccountConfigMissingProperties() throws JsonProcessingException {
        // given
        final String invalidAccountConfig = """
                {
                    "mraid-script-pattern": "<script src=\\"mraid.js\\"></script>"
                }
                """;
        final ObjectNode objectNode = OBJECT_MAPPER.readValue(invalidAccountConfig, ObjectNode.class);

        // when
        final PbRichMediaFilterProperties actualProperties = target.resolve(objectNode);

        // then
        assertThat(actualProperties).isEqualTo(GLOBAL_PROPERTIES);
    }

}
