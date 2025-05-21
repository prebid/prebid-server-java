package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Uid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.prebid.server.hooks.modules.optable.targeting.model.Id;
import org.prebid.server.hooks.modules.optable.targeting.model.OS;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class IdsMapper {

    private final ObjectMapper objectMapper;

    public IdsMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public List<Id> toIds(BidRequest bidRequest, Map<String, String> ppidMapping) {
        final IdsResolver idsResolver = IdsResolver.of(objectMapper, bidRequest);

        final Map<String, String> ids = applyStaticMapping(idsResolver);
        final Map<String, String> dynamicIds = applyDynamicMapping(idsResolver, ppidMapping);
        if (dynamicIds != null && !dynamicIds.isEmpty()) {
            ids.putAll(dynamicIds);
        }

        return ids.entrySet().stream()
                .filter(it -> it.getValue() != null)
                .map(it -> Id.of(it.getKey(), it.getValue()))
                .toList();
    }

    private Map<String, String> toIds(List<Eid> eids, Map<String, String> ppidMapping) {
        if (ppidMapping == null || ppidMapping.isEmpty() || CollectionUtils.isEmpty(eids)) {
            return null;
        }

        return eids.stream()
                .map(eid -> eidSourceToFirstUidId(eid, ppidMapping))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
    }

    private Pair<String, String> eidSourceToFirstUidId(Eid eid, Map<String, String> ppidMapping) {
        final String key = ppidMapping.get(eid.getSource());

        return key != null ? Pair.of(key, getFirstUidId(eid)) : null;
    }

    private String getFirstUidId(Eid eid) {
        return eid.getUids().stream().findFirst().map(Uid::getId).orElse(null);
    }

    private Map<String, String> applyDynamicMapping(IdsResolver idsResolver, Map<String, String> ppidMapping) {
        return toIds(idsResolver.getEIDs(), ppidMapping);
    }

    private Map<String, String> applyStaticMapping(IdsResolver idsResolver) {
        return new HashMap<>() {{
                put(Id.EMAIL, idsResolver.getEmail());
                put(Id.PHONE, idsResolver.getPhone());
                put(Id.ZIP, idsResolver.getZip());
                put(Id.APPLE_IDFA, idsResolver.getDeviceIfa(OS.IOS));
                put(Id.GOOGLE_GAID, idsResolver.getDeviceIfa(OS.ANDROID));
                put(Id.ROKU_RIDA, idsResolver.getDeviceIfa(OS.ROKU));
                put(Id.SAMSUNG_TV_TIFA, idsResolver.getDeviceIfa(OS.TIZEN));
                put(Id.AMAZON_FIRE_AFAI, idsResolver.getDeviceIfa(OS.FIRE));
                put(Id.NET_ID, idsResolver.getNetId());
                put(Id.ID5, idsResolver.getID5());
                put(Id.UTIQ, idsResolver.getUtiq());
                put(Id.OPTABLE_VID, idsResolver.getOptableVID()); }};
    }
}
