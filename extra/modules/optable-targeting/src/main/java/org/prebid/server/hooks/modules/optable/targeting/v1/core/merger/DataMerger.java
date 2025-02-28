package org.prebid.server.hooks.modules.optable.targeting.v1.core.merger;

import com.iab.openrtb.request.Segment;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Data;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class DataMerger extends BaseMerger {

    public List<com.iab.openrtb.request.Data> merge(List<com.iab.openrtb.request.Data> destination, List<Data> source) {
        if (CollectionUtils.isEmpty(source)) {
            return destination;
        }

        final Map<String, com.iab.openrtb.request.Data> idToData = mapDataToId(destination);
        if (idToData == null || idToData.isEmpty()) {
            return source.stream().map(this::toData).toList();
        }

        source.forEach(data -> idToData.compute(data.getId(), (id, item) -> item != null
                ? mergeData(item, data)
                : toData(data)));

        return idToData.values().stream().toList();
    }

    private com.iab.openrtb.request.Data mergeData(com.iab.openrtb.request.Data destination, Data source) {
        if (source == null) {
            return destination;
        }

        final Map<String, Segment> idToSegment = mapSegmentToId(destination.getSegment());
        if (idToSegment == null) {
            return toData(source);
        }

        Optional.ofNullable(source.getSegment()).ifPresent(it ->
                it.forEach(seg -> idToSegment.compute(seg.getId(), (id, item) -> item != null
                        ? mergeSegment(item, seg)
                        : toSegment(seg))));

        return destination.toBuilder()
                .segment(idToSegment.values().stream().toList())
                .build();
    }

    private Segment mergeSegment(Segment destination,
                                 org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Segment source) {

        return Segment.builder()
                .id(destination.getId())
                .value(destination.getValue())
                .name(destination.getName())
                .ext(mergeExt(destination.getExt(), source.getExt()))
                .build();
    }

    private Segment toSegment(org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Segment segment) {
        return Segment.builder()
                .id(segment.getId())
                .ext(segment.getExt())
                .build();
    }

    private Map<String, Segment> mapSegmentToId(List<Segment> segment) {
        return CollectionUtils.isNotEmpty(segment)
                ? segment.stream().collect(Collectors.toMap(Segment::getId, it -> it))
                : null;
    }

    private com.iab.openrtb.request.Data toData(Data data) {
        if (data == null) {
            return null;
        }

        final List<Segment> segment = Optional.of(data)
                .map(Data::getSegment)
                .map(it -> it.stream()
                        .map(seg -> Segment.builder()
                                .id(seg.getId())
                                .ext(seg.getExt())
                                .build())
                        .toList())
                .orElse(null);

        return com.iab.openrtb.request.Data.builder()
                .id(data.getId())
                .segment(segment)
                .build();
    }

    private Map<String, com.iab.openrtb.request.Data> mapDataToId(List<com.iab.openrtb.request.Data> data) {
        return CollectionUtils.isNotEmpty(data)
                ? data.stream().collect(Collectors.toMap(com.iab.openrtb.request.Data::getId, it -> it))
                : null;
    }
}
