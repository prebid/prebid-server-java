package org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Collection;

@Builder
public record EnrichmentResult<DeviceInfoBox>(
    DeviceInfoBox enrichedDevice,
    Collection<String> enrichedFields,
    Exception processingException
) {}
