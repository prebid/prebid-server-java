package org.prebid.server.hooks.modules.intentiq.identity.v1.core;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Uid;
import com.iab.openrtb.request.User;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.intentiq.identity.cache.CacheKey;
import org.prebid.server.hooks.modules.intentiq.identity.cache.KeyType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Derives the ordered list of alias cache keys for a bid request. Each relevant first-party id present
 * becomes one namespaced key; the resolved identity is aliased across all of them so a later request
 * carrying any of those ids hits the cache.
 *
 * <p>Priority (highest first): IIQ eid, shared id / pubcid, device MAID, any other eid source, then a
 * probabilistic device composite as last resort. Keys are lower-cased, de-duplicated (first occurrence
 * wins) and capped at {@code maxKeys}. {@code device.lmt == 1} suppresses the MAID key; CTV ifas are
 * upper-cased to match the resolution request's {@code idtype 8} handling.
 */
public class FirstPartyKeyExtractor {

    private static final String IIQ_SOURCE = "intentiq.com";
    private static final Set<String> SHARED_SOURCES = Set.of("pubcid.org", "sharedid.org");

    private final int maxKeys;

    public FirstPartyKeyExtractor(int maxKeys) {
        this.maxKeys = maxKeys;
    }

    public List<CacheKey> candidateKeys(BidRequest bidRequest) {
        final List<CacheKey> keys = new ArrayList<>();
        final User user = bidRequest.getUser();
        final List<Eid> eids = user != null ? user.getEids() : null;
        final Device device = bidRequest.getDevice();

        addEidKeys(keys, eids, "iiq", KeyType.THIRD_PARTY, IIQ_SOURCE::equals);
        addEidKeys(keys, eids, "pubcid", KeyType.FIRST_PARTY, SHARED_SOURCES::contains);
        addMaidKey(keys, device);
        addOtherEidKeys(keys, eids);
        addDeviceComposite(keys, device);

        return dedupAndCap(keys);
    }

    private static void addEidKeys(List<CacheKey> keys,
                                   List<Eid> eids,
                                   String namespace,
                                   KeyType type,
                                   java.util.function.Predicate<String> sourceMatch) {
        if (eids == null) {
            return;
        }
        eids.stream()
                .filter(eid -> eid != null && eid.getSource() != null && sourceMatch.test(eid.getSource()))
                .map(Eid::getUids)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .map(Uid::getId)
                .filter(StringUtils::isNotBlank)
                .forEach(id -> keys.add(new CacheKey(namespace + ":" + id, type)));
    }

    private static void addMaidKey(List<CacheKey> keys, Device device) {
        if (device == null || StringUtils.isBlank(device.getIfa()) || Integer.valueOf(1).equals(device.getLmt())) {
            return;
        }
        final Integer deviceType = device.getDevicetype();
        final boolean ctv = deviceType != null && (deviceType == 3 || deviceType == 7);
        final String ifa = ctv ? device.getIfa().toUpperCase(Locale.ROOT) : device.getIfa();
        keys.add(new CacheKey("maid:" + ifa, KeyType.FIRST_PARTY));
    }

    private static void addOtherEidKeys(List<CacheKey> keys, List<Eid> eids) {
        if (eids == null) {
            return;
        }
        for (Eid eid : eids) {
            if (eid == null || eid.getSource() == null || eid.getUids() == null) {
                continue;
            }
            final String source = eid.getSource();
            if (IIQ_SOURCE.equals(source) || SHARED_SOURCES.contains(source)) {
                continue;
            }
            final String namespace = source.toLowerCase(Locale.ROOT);
            for (Uid uid : eid.getUids()) {
                if (uid != null && StringUtils.isNotBlank(uid.getId())) {
                    keys.add(new CacheKey(namespace + ":" + uid.getId(), KeyType.FIRST_PARTY));
                }
            }
        }
    }

    private static void addDeviceComposite(List<CacheKey> keys, Device device) {
        if (device == null) {
            return;
        }
        final String ip = StringUtils.isNotBlank(device.getIp()) ? device.getIp() : device.getIpv6();
        // Normalized UA fields (not the raw UA string) to avoid fragmenting the cache per request.
        final String ua = DeviceUserAgent.normalize(device.getUa());
        final String composite = Stream.of(device.getIfa(), StringUtils.trimToNull(ua), ip)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("_"));
        if (!composite.isEmpty()) {
            keys.add(new CacheKey("dev:" + composite, KeyType.DEVICE));
        }
    }

    private List<CacheKey> dedupAndCap(List<CacheKey> keys) {
        final Map<String, CacheKey> unique = new LinkedHashMap<>();
        for (CacheKey key : keys) {
            unique.putIfAbsent(key.key(), key);
        }
        return unique.values().stream().limit(maxKeys).collect(Collectors.toList());
    }
}
