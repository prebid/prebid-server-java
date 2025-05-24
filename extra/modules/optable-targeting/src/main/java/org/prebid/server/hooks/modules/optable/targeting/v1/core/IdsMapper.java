package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Uid;
import com.iab.openrtb.request.User;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.optable.targeting.model.Id;
import org.prebid.server.hooks.modules.optable.targeting.model.OS;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.ExtUserOptable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class IdsMapper {

    private static final Map<String, String> STATIC_PPID_MAPPING = Map.of(
            "id5-sync.com", Id.ID5,
            "utiq.com", Id.UTIQ,
            "netid.de", Id.NET_ID);

    private final ObjectMapper objectMapper;

    public IdsMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public List<Id> toIds(BidRequest bidRequest, Map<String, String> ppidMapping) {
        final User user = bidRequest.getUser();

        final Map<String, String> ids = new HashMap<>();
        addOptableIds(ids, user);
        addDeviceIds(ids, bidRequest.getDevice());
        addEidsIds(ids, user, STATIC_PPID_MAPPING);
        addEidsIds(ids, user, ppidMapping);

        return ids.entrySet().stream()
                .map(it -> Id.of(it.getKey(), it.getValue()))
                .toList();
    }

    private void addOptableIds(Map<String, String> ids, User user) {
        final Optional<ExtUserOptable> extUserOptable = Optional.ofNullable(user)
                .map(User::getExt)
                .map(ext -> ext.getProperty("optable"))
                .map(this::parseExtUserOptable);

        extUserOptable.map(ExtUserOptable::getEmail).ifPresent(it -> ids.put(Id.EMAIL, it));
        extUserOptable.map(ExtUserOptable::getPhone).ifPresent(it -> ids.put(Id.PHONE, it));
        extUserOptable.map(ExtUserOptable::getZip).ifPresent(it -> ids.put(Id.ZIP, it));
        extUserOptable.map(ExtUserOptable::getVid).ifPresent(it -> ids.put(Id.OPTABLE_VID, it));
    }

    private ExtUserOptable parseExtUserOptable(JsonNode node) {
        try {
            return objectMapper.treeToValue(node, ExtUserOptable.class);
        } catch (JsonProcessingException e) {
            // TODO: you need to handle this exception in some way
            throw new RuntimeException(e);
        }
    }

    private static void addDeviceIds(Map<String, String> ids, Device device) {
        final String ifa = device != null ? device.getIfa() : null;
        final String os = device != null ? StringUtils.toRootLowerCase(device.getOs()) : null;
        final int lmt = Optional.ofNullable(device).map(Device::getLmt).orElse(0);

        if (ifa == null || StringUtils.isEmpty(os) || lmt == 1) {
            return;
        }

        if (os.contains(OS.IOS.getValue())) {
            ids.put(Id.APPLE_IDFA, ifa);
        }
        if (os.contains(OS.ANDROID.getValue())) {
            ids.put(Id.GOOGLE_GAID, ifa);
        }
        if (os.contains(OS.ROKU.getValue())) {
            ids.put(Id.ROKU_RIDA, ifa);
        }
        if (os.contains(OS.TIZEN.getValue())) {
            ids.put(Id.SAMSUNG_TV_TIFA, ifa);
        }
        if (os.contains(OS.FIRE.getValue())) {
            ids.put(Id.AMAZON_FIRE_AFAI, ifa);
        }
    }

    private static void addEidsIds(Map<String, String> ids, User user, Map<String, String> ppidMapping) {
        final List<Eid> eids = user != null ? user.getEids() : null;
        if (MapUtils.isEmpty(ppidMapping) || CollectionUtils.isEmpty(eids)) {
            return;
        }

        for (Eid eid : eids) {
            final String source = eid != null ? eid.getSource() : null;
            if (source == null) {
                continue;
            }

            final String idKey = ppidMapping.get(source);
            if (idKey != null) {
                firstUidId(eid).ifPresent(it -> ids.put(idKey, it));
            }
        }
    }

    private static Optional<String> firstUidId(Eid eid) {
        return Optional.ofNullable(eid.getUids())
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .findFirst()
                .map(Uid::getId);
    }
}
