package org.prebid.server.spring.config.bidder.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.mergepatch.JsonMergePatch;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;

public class BidderConfigurationMerger {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private BidderConfigurationMerger() {

    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <CFG extends BidderConfigurationProperties> CFG mergeConfigurations(CFG mergeTo, CFG toMerge) {
        try {
            final JsonNode mergedNode = JsonMergePatch
                    .fromJson(MAPPER.valueToTree(mergeTo))
                    .apply(MAPPER.valueToTree(toMerge));

            return (CFG) MAPPER.treeToValue(mergedNode, (Class) toMerge.getSelfClass());
        } catch (JsonPatchException | JsonProcessingException e) {
            throw new IllegalArgumentException("Exception occurred while merging configurations", e);
        }
    }
}
