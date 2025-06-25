package org.prebid.server.hooks.modules.optable.targeting.v1.core.merger;

import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Segment;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class DataMerger {

    private DataMerger() {
    }

    public static List<Data> merge(List<Data> destination,
                                                           List<Data> source) {

        if (CollectionUtils.isEmpty(source)) {
            return destination;
        }

        final Map<String, Data> idToData = mapDataToId(destination);
        if (idToData == null || idToData.isEmpty()) {
            return source;
        }

        source.forEach(data -> idToData.compute(data.getId(), (id, item) -> item != null
                ? mergeData(item, data)
                : data));

        return idToData.values().stream().toList();
    }

    private static Data mergeData(Data destination, Data source) {
        if (source == null) {
            return destination;
        }

        final Map<String, Segment> idToSegment = mapSegmentToId(destination.getSegment());
        if (idToSegment == null) {
            return source;
        }

        Optional.ofNullable(source.getSegment()).ifPresent(it ->
                it.forEach(seg -> idToSegment.compute(seg.getId(), (id, item) -> item != null
                        ? mergeSegment(item, seg)
                        : seg)));

        return destination.toBuilder()
                .segment(idToSegment.values().stream().toList())
                .build();
    }

    private static Segment mergeSegment(Segment destination, Segment source) {

        return Segment.builder()
                .id(destination.getId())
                .value(destination.getValue())
                .name(destination.getName())
                .ext(ExtMerger.mergeExt(destination.getExt(), source.getExt()))
                .build();
    }

    private static Map<String, Segment> mapSegmentToId(List<Segment> segment) {
        return CollectionUtils.isNotEmpty(segment)
                ? segment.stream().collect(Collectors.toMap(Segment::getId, it -> it))
                : null;
    }

    private static Map<String, Data> mapDataToId(List<Data> data) {
        return CollectionUtils.isNotEmpty(data)
                ? data.stream().collect(Collectors.toMap(Data::getId, it -> it))
                : null;
    }
}
