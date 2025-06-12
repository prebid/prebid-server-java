package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

@Builder
@Slf4j
public class ExtWURFLMapper {

    private final Set<String> staticCaps;
    private final Set<String> virtualCaps;
    private boolean addExtCaps;
    private final com.scientiamobile.wurfl.core.Device wurflDevice;
    private static final String NULL_VALUE_TOKEN = "$null$";
    private static final String WURFL_ID_PROPERTY = "wurfl_id";

    public JsonNode mapExtProperties() {

        final ObjectMapper objectMapper = new ObjectMapper();
        final ObjectNode wurflNode = objectMapper.createObjectNode();

        wurflNode.put(WURFL_ID_PROPERTY, wurflDevice.getId());

        if (addExtCaps) {

            for (String capability : staticCaps) {
                try {
                    final String value = wurflDevice.getCapability(capability);
                    if (value != null && !NULL_VALUE_TOKEN.equals(value)) {
                        wurflNode.put(capability, value);
                    }
                } catch (Exception e) {
                    log.warn("Error getting capability for {}: {}", capability, e.getMessage());
                }
            }

            for (String virtualCapability : virtualCaps) {
                try {
                    final String value = wurflDevice.getVirtualCapability(virtualCapability);
                    if (value != null) {
                        wurflNode.put(virtualCapability, value);
                    }
                } catch (Exception e) {
                    log.warn("Could not fetch virtual capability {}", virtualCapability);
                }
            }
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
