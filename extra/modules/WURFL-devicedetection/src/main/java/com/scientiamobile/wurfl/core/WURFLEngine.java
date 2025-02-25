package com.scientiamobile.wurfl.core;

import com.scientiamobile.wurfl.core.cache.CacheProvider;

import java.util.Map;
import java.util.Set;

public interface WURFLEngine {
    Set<String> getAllCapabilities();
    Set<String> getAllVirtualCapabilities();
    void load();
    void setCacheProvider(CacheProvider cacheProvider);
    Device getDeviceById(String deviceId);
    Device getDeviceForRequest(Map<String,String> headers);
}
