package org.prebid.server.gdpr.vendorlist;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import org.prebid.server.execution.Timeout;
import org.prebid.server.gdpr.vendorlist.proto.VendorListInfo;

import java.util.Map;
import java.util.Objects;

public class InMemoryCacheVendorList implements VendorList {

    private final VendorList delegate;
    private final Map<Integer, VendorListInfo> versionToVendorList;

    public InMemoryCacheVendorList(int size, VendorList delegate) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        versionToVendorList = createCache(size);
        this.delegate = Objects.requireNonNull(delegate);
    }

    private static <T> Map<Integer, T> createCache(int size) {
        return Caffeine.newBuilder()
                .maximumSize(size)
                .<Integer, T>build()
                .asMap();
    }

    @Override
    public Future<VendorListInfo> forVersion(int version, Timeout timeout) {
        final VendorListInfo vendorListInfo = versionToVendorList.get(version);

        return vendorListInfo != null
                ? Future.succeededFuture(vendorListInfo)
                : delegate.forVersion(version, timeout)
                .map(foundVendorListInfo -> saveToCache(version, foundVendorListInfo));
    }

    private VendorListInfo saveToCache(int version, VendorListInfo vendorListInfo) {
        // FIXME: put to cache only known vendors (gdpr.host-vendor-id and all usersyncer.gdprVendorId) from list
        versionToVendorList.put(version, vendorListInfo);
        return vendorListInfo;
    }
}
