package org.prebid.server.hooks.modules.optable.targeting.v1.core.merger;

import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Uid;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class EidsMerger extends BaseMerger {

    public List<Eid> merge(List<Eid> destination, List<Eid> source) {
        if (CollectionUtils.isEmpty(source)) {
            return destination;
        }

        final Map<String, Eid> sourceToEid = mapEidToSource(destination);

        if (sourceToEid == null || sourceToEid.isEmpty()) {
            return source;
        }

        source.forEach(eid -> sourceToEid.compute(eid.getSource(), (id, item) -> item != null
                            ? mergeEids(item, eid)
                            : eid));

        return sourceToEid.values().stream().toList();
    }

    private Eid mergeEids(Eid destination, Eid source) {
        if (source == null) {
            return destination;
        }

        final Map<String, Uid> idToUid = mapUidToId(destination.getUids());

        if (idToUid == null || idToUid.isEmpty()) {
            return source;
        }

        Optional.ofNullable(source.getUids())
                .ifPresent(it -> it.forEach(uid -> idToUid.compute(uid.getId(), (id, item) -> item != null
                        ? mergeUids(item, uid)
                        : uid)));

        return destination.toBuilder()
                .uids(idToUid.values().stream().toList())
                .build();
    }

    private Uid mergeUids(Uid destination, Uid source) {
        return destination.toBuilder()
                .atype(source.getAtype())
                .ext(mergeExt(destination.getExt(), source.getExt()))
                .build();
    }

    private Map<String, Eid> mapEidToSource(List<Eid> eids) {
        return CollectionUtils.isNotEmpty(eids)
                ? eids.stream().collect(Collectors.toMap(Eid::getSource, eid -> eid))
                : null;
    }

    private Map<String, Uid> mapUidToId(List<Uid> uids) {
        return CollectionUtils.isNotEmpty(uids)
                ? uids.stream().collect(Collectors.toMap(Uid::getId, uid -> uid))
                : null;
    }
}
