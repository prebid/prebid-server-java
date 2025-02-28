package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Uid;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.prebid.server.hooks.modules.optable.targeting.model.Id;
import org.prebid.server.hooks.modules.optable.targeting.model.OS;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@AllArgsConstructor
public class IdsMapper {

    private final ObjectMapper objectMapper;

    private final Map<String, String> ppidMapping;

    public List<Id> toIds(BidRequest bidRequest) {
        final IdsResolver idsResolver = IdsResolver.of(objectMapper, bidRequest);

        final Map<String, String> ids = applyStaticMapping(idsResolver);
        final Map<String, String> dynamicIds = applyDynamicMapping(idsResolver);
        if (dynamicIds != null && !dynamicIds.isEmpty()) {
            ids.putAll(dynamicIds);
        }

        return ids.entrySet().stream()
                .filter(it -> it.getValue() != null)
                .map(it -> Id.of(it.getKey(), it.getValue()))
                .toList();
    }

    private Map<String, String> toIds(List<Eid> eids) {
        if (ppidMapping == null || ppidMapping.isEmpty() || CollectionUtils.isEmpty(eids)) {
            return null;
        }

        return eids.stream().map(it -> {
            final String key = ppidMapping.get(it.getSource());

            if (key != null) {
                return Pair.of(key, it.getUids().stream().findFirst().map(Uid::getId).orElse(null));
            }

            return null;
        }).filter(Objects::nonNull).collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
    }

    private Map<String, String> applyDynamicMapping(IdsResolver idsResolver) {
        return toIds(idsResolver.getEIDs());
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
