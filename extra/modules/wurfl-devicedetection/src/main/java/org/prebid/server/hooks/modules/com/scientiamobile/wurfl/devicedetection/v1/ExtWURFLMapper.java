package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.prebid.server.json.ObjectMapperProvider;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;

import java.util.Set;

@Builder
public class ExtWURFLMapper {

    private static final Logger LOG = LoggerFactory.getLogger(ExtWURFLMapper.class);

    private final Set<String> staticCaps;
    private final Set<String> virtualCaps;
    private boolean addExtCaps;
    private final com.scientiamobile.wurfl.core.Device wurflDevice;

    private static final String WURFL_ID_PROPERTY = "wurfl_id";

    public ObjectNode mapExtProperties() {

        final ObjectMapper objectMapper = ObjectMapperProvider.mapper();
        final ObjectNode wurflNode = objectMapper.createObjectNode();

        wurflNode.put(WURFL_ID_PROPERTY, wurflDevice.getId());

        if (addExtCaps) {

            for (String capability : staticCaps) {
                try {
                    final String value = wurflDevice.getCapability(capability);
                    if (value != null) {
                        wurflNode.put(capability, value);
                    }
                } catch (Exception e) {
                    LOG.warn("Error getting capability for {}: {}", capability, e.getMessage());
                }
            }

            for (String virtualCapability : virtualCaps) {
                try {
                    final String value = wurflDevice.getVirtualCapability(virtualCapability);
                    if (value != null) {
                        wurflNode.put(virtualCapability, value);
                    }
                } catch (Exception e) {
                    LOG.warn("Could not fetch virtual capability {}", virtualCapability);
                }
            }
        }

        return wurflNode;
    }
}
