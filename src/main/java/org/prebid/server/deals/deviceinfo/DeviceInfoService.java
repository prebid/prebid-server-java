package org.prebid.server.deals.deviceinfo;

import io.vertx.core.Future;
import org.prebid.server.deals.model.DeviceInfo;

/**
 * Processes device related information.
 */
@FunctionalInterface
public interface DeviceInfoService {

    /**
     * Provides information about device based on User-Agent string and other available attributes.
     */
    Future<DeviceInfo> getDeviceInfo(String ua);
}
