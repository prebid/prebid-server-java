package org.prebid.server.hooks.modules.optable.targeting.v1.core.merger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

public class BaseMergerTest {

    protected final ObjectMapper mapper = new ObjectMapper();

    protected ObjectNode givenExt(Map<String, String> fields) {
        final ObjectNode ext = mapper.createObjectNode();
        fields.forEach(ext::put);

        return ext;
    }
}
