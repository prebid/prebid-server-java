package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Builder
@Slf4j
public class ExtWURFLMapper {

    private final List<String> staticCaps;
    private final List<String> virtualCaps;
    private boolean addExtCaps;
    private final com.scientiamobile.wurfl.core.Device wurflDevice;
    private static final String NULL_VALUE_TOKEN = "$null$";
    private static final String WURFL_ID_PROPERTY = "wurfl_id";

    public JsonNode mapExtProperties() {

        final ObjectMapper objectMapper = new ObjectMapper();
        final ObjectNode wurflNode = objectMapper.createObjectNode();

        try {
            wurflNode.put(WURFL_ID_PROPERTY, wurflDevice.getId());

            if (addExtCaps) {

                staticCaps.stream()
                        .map(sc -> {
                            try {
                                return Map.entry(sc, wurflDevice.getCapability(sc));
                            } catch (Exception e) {
                                log.error("Error getting capability for {}: {}", sc, e.getMessage());
                                return Map.entry(sc, NULL_VALUE_TOKEN);
                            }
                        })
                        .filter(entry -> entry.getValue() != null
                                && !NULL_VALUE_TOKEN.equals(entry.getValue()))
                        .forEach(entry -> wurflNode.put(entry.getKey(), entry.getValue()));

                virtualCaps.stream()
                        .map(vc -> {
                            try {
                                return Map.entry(vc, wurflDevice.getVirtualCapability(vc));
                            } catch (Exception e) {

                                log.warn("Could not fetch virtual capability " + vc);
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .forEach(entry -> wurflNode.put(entry.getKey(), entry.getValue()));
            }
        } catch (Exception e) {
            log.error("Exception while updating EXT");
        }

        JsonNode node = null;
        try {
            node = objectMapper.readTree(wurflNode.toString());
        } catch (JsonProcessingException e) {
            log.error("Error creating WURFL ext device JSON:  {}", e.getMessage());
        }
        return node;
    }
}
