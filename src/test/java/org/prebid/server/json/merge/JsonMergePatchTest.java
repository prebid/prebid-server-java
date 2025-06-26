package org.prebid.server.json.merge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatchException;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonMergePatchTest extends VertxTest {

    @Test
    public void fromJsonShouldParseDoublesCorrectly() throws JsonProcessingException, JsonPatchException {
        // given
        final String source = """
                {
                    "object": {
                        "property": 0.08
                    }
                }
                """;

        final JsonNode givenSource = mapper.readTree(source);
        final JsonNode givenTarget = mapper.readTree("{}");

        // when
        final JsonNode oldPatch = com.github.fge.jsonpatch.mergepatch.JsonMergePatch.fromJson(givenSource)
                .apply(givenTarget);
        final JsonNode newPatch = JsonMergePatch.fromJson(givenSource)
                .apply(givenTarget);

        // then
        assertThat(givenSource).isEqualTo(newPatch);
        assertThat(givenSource).isNotEqualTo(oldPatch);
    }
}
