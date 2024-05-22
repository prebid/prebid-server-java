package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.detection;

import fiftyone.devicedetection.shared.DeviceData;
import fiftyone.pipeline.core.data.FlowData;
import fiftyone.pipeline.core.flowelements.Pipeline;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.adapters.DeviceDataWrapper;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.adapters.DeviceInfoBuilderMethodSet;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.device.DeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.device.WritableDeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.mergers.PropertyMerge;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.EnrichmentResult;

import java.math.BigDecimal;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Map.entry;

public class DeviceRefinerImp implements DeviceRefiner {
    private final Supplier<Pipeline> pipelineSupplier;

    public DeviceRefinerImp(
            Supplier<Pipeline> pipelineSupplier)
    {
        this.pipelineSupplier = pipelineSupplier;
    }

    public <DeviceInfoBox, DeviceInfoBoxBuilder> EnrichmentResult<DeviceInfoBox> enrichDeviceInfo(
            DeviceInfo rawDeviceInfo,
            CollectedEvidence collectedEvidence,
            DeviceInfoBuilderMethodSet<DeviceInfoBox, DeviceInfoBoxBuilder>.Adapter writableAdapter)
    {
        final EnrichmentResult.EnrichmentResultBuilder<DeviceInfoBox> resultBuilder = EnrichmentResult.builder();

        final Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> patchPlan = buildPatchPlanFor(rawDeviceInfo);
        if (patchPlan == null || patchPlan.isEmpty()) {
            return resultBuilder.build();
        }

        if (populateDeviceInfo(writableAdapter, collectedEvidence, patchPlan, resultBuilder)) {
            resultBuilder.enrichedDevice(writableAdapter.rebuildBox());
        }

        return resultBuilder.build();
    }

    private static final Map<String, PropertyMerge<WritableDeviceInfo, DeviceInfo, ?>> propertiesToMerge = Map.ofEntries(
            entry("DeviceType", new PropertyMerge<>(DeviceInfo::getDeviceType, v -> v > 0, WritableDeviceInfo::setDeviceType)),
            entry("Make", new PropertyMerge<>(DeviceInfo::getMake, s -> !s.isEmpty(), WritableDeviceInfo::setMake)),
            entry("Model", new PropertyMerge<>(DeviceInfo::getModel, s -> !s.isEmpty(), WritableDeviceInfo::setModel)),
            entry("Os", new PropertyMerge<>(DeviceInfo::getOs, s -> !s.isEmpty(), WritableDeviceInfo::setOs)),
            entry("Osv", new PropertyMerge<>(DeviceInfo::getOsv, s -> !s.isEmpty(), WritableDeviceInfo::setOsv)),
            entry("H", new PropertyMerge<>(DeviceInfo::getH, v -> v > 0, WritableDeviceInfo::setH)),
            entry("W", new PropertyMerge<>(DeviceInfo::getW, v -> v > 0, WritableDeviceInfo::setW)),
            entry("Ppi", new PropertyMerge<>(DeviceInfo::getPpi, v -> v > 0, WritableDeviceInfo::setPpi)),
            entry("PixelRatio", new PropertyMerge<>(DeviceInfo::getPixelRatio, (BigDecimal v) -> v.intValue() > 0, WritableDeviceInfo::setPixelRatio)),
            entry("DeviceID", new PropertyMerge<>(DeviceInfo::getDeviceId, s -> !s.isEmpty(), WritableDeviceInfo::setDeviceId))
    );

    protected Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> buildPatchPlanFor(
            DeviceInfo deviceInfo
    ) {
        return propertiesToMerge.entrySet().stream()
                .filter(nextMerge -> nextMerge.getValue().shouldReplacePropertyIn(deviceInfo))
                .map(e -> new AbstractMap.SimpleEntry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>(
                        e.getKey(),
                        e.getValue()::copySingleValue))
                .collect(Collectors.toUnmodifiableList());
    }

    protected boolean populateDeviceInfo(
            WritableDeviceInfo writableDeviceInfo,
            CollectedEvidence collectedEvidence,
            Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> devicePatchPlan,
            EnrichmentResult.EnrichmentResultBuilder<?> enrichmentResultBuilder)
    {
        try (FlowData data = pipelineSupplier.get().createFlowData()) {
            data.addEvidence(pickRelevantFrom(collectedEvidence));
            data.process();
            DeviceData device = data.get(DeviceData.class);
            if (device == null) {
                return false;
            }
            return patchDeviceInfo(
                    writableDeviceInfo,
                    devicePatchPlan,
                    new DeviceDataWrapper(device),
                    enrichmentResultBuilder
            );
        } catch (Exception e) {
            enrichmentResultBuilder.processingException(e);
            return false;
        }
    }

    protected Map<String, String> pickRelevantFrom(CollectedEvidence collectedEvidence) {
        final Map<String, String> evidence = new HashMap<>();

        final String ua = collectedEvidence.deviceUA();
        if (ua != null && !ua.isEmpty()) {
            evidence.put("header.user-agent", ua);
        }
        final Map<String, String> secureHeaders = collectedEvidence.secureHeaders();
        if (secureHeaders != null && !secureHeaders.isEmpty()) {
            evidence.putAll(secureHeaders);
        }
        if (!evidence.isEmpty()) {
            return evidence;
        }

        final Collection<Map.Entry<String, String>> headers = collectedEvidence.rawHeaders();
        if (headers != null && !headers.isEmpty()) {
            for(Map.Entry<String, String> nextRawHeader : headers) {
                evidence.put("header." + nextRawHeader.getKey(), nextRawHeader.getValue());
            }
        }

        return evidence;
    }

    protected boolean patchDeviceInfo(
            WritableDeviceInfo writableDeviceInfo,
            Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> patchPlan,
            DeviceInfo newData,
            EnrichmentResult.EnrichmentResultBuilder<?> enrichmentResultBuilder)
    {
        final List<String> patchedFields = new ArrayList<>();
        for (Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>> namedPatch : patchPlan) {
            final boolean propChanged = namedPatch.getValue().test(writableDeviceInfo, newData);
            if (propChanged) {
                patchedFields.add(namedPatch.getKey());
            }
        }
        if (patchedFields.isEmpty()) {
            return false;
        }

        enrichmentResultBuilder.enrichedFields(patchedFields);
        return true;
    }
}
