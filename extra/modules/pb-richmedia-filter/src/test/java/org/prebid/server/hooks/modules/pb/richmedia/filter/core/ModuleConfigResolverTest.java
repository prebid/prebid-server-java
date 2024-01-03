package org.prebid.server.hooks.modules.pb.richmedia.filter.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;
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

    @Before
    public void before() {
        target = new ModuleConfigResolver(OBJECT_MAPPER, GLOBAL_PROPERTIES);
    }

    @Test
    public void resolveShouldReturnAccountConfigWhenAccountConfigIsPresent() throws JsonProcessingException {
        final String accountConfig = """
                {
                   "hooks": {
                      "modules": {
                         "pb-richmedia-filter": {
                            "filter-mraid": true,
                            "mraid-script-pattern": "<script src=\\"mraid.js\\"></script>"
                         }
                     }
                   }
                }
                """;
        final ObjectNode objectNode = OBJECT_MAPPER.readValue(accountConfig, ObjectNode.class);
        final PbRichMediaFilterProperties actualProperties = target.resolve(objectNode);
        assertThat(actualProperties).isEqualTo(ACCOUNT_PROPERTIES);
    }

    @Test
    public void resolveShouldReturnGlobalConfigWhenAccountConfigIsAbsent() {
        final ObjectNode emptyObjectNode = OBJECT_MAPPER.createObjectNode();
        final PbRichMediaFilterProperties actualProperties = target.resolve(emptyObjectNode);
        assertThat(actualProperties).isEqualTo(GLOBAL_PROPERTIES);
    }

    @Test
    public void resolveShouldReturnGlobalConfigWhenAccountConfigCanNotBeFoundByPath() throws JsonProcessingException {
        final String invalidAccountConfig = """
                {
                   "hook": {
                      "modules": {
                         "pb-richmedia-filter": {
                            "filter-mraid": true,
                            "mraid-script-pattern": "<script src=\\"mraid.js\\"></script>"
                         }
                     }
                   }
                }
                """;
        final ObjectNode objectNode = OBJECT_MAPPER.readValue(invalidAccountConfig, ObjectNode.class);
        final PbRichMediaFilterProperties actualProperties = target.resolve(objectNode);
        assertThat(actualProperties).isEqualTo(GLOBAL_PROPERTIES);
    }

    @Test
    public void resolveShouldReturnGlobalConfigWhenAccountConfigCanNotBeParsed() throws JsonProcessingException {
        final String invalidAccountConfig = """
                {
                   "hooks": {
                      "modules": {
                         "pb-richmedia-filter": {
                            "filter-mraid": "invalid_type",
                            "mraid-script-pattern": "<script src=\\"mraid.js\\"></script>"
                         }
                     }
                   }
                }
                """;
        final ObjectNode objectNode = OBJECT_MAPPER.readValue(invalidAccountConfig, ObjectNode.class);
        final PbRichMediaFilterProperties actualProperties = target.resolve(objectNode);
        assertThat(actualProperties).isEqualTo(GLOBAL_PROPERTIES);
    }

}
