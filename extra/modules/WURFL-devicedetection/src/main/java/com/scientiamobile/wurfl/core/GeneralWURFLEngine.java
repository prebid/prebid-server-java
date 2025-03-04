package com.scientiamobile.wurfl.core;

import com.scientiamobile.wurfl.core.cache.CacheProvider;
import com.scientiamobile.wurfl.core.exc.CapabilityNotDefinedException;
import com.scientiamobile.wurfl.core.exc.VirtualCapabilityNotDefinedException;
import com.scientiamobile.wurfl.core.matchers.MatchType;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GeneralWURFLEngine implements WURFLEngine {

    public GeneralWURFLEngine(String wurflPath) { }

    public static void wurflDownload(String wurflUrl, String dest) {
    }

    static final Map<String, String> CAPABILITIES = Map.ofEntries(
            Map.entry("brand_name", "Google"),
            Map.entry("model_name", "Pixel 9 Pro XL"),
            Map.entry("device_os", "Android"),
            Map.entry("device_os_version", "15.0"),
            Map.entry("pointing_method", "touchscreen"),
            Map.entry("is_wireless_device", "true"),
            Map.entry("is_smarttv", "false"),
            Map.entry("density_class", "2.55"),
            Map.entry("resolution_width", "1344"),
            Map.entry("resolution_height", "2992"),
            Map.entry("ux_full_desktop", "false"),
            Map.entry("marketing_name", ""),
            Map.entry("mobile_browser", "Chrome Mobile"),
            Map.entry("mobile_browser_version", ""),
            Map.entry("preferred_markup", "html_web_4_0"),
            Map.entry("is_connected_tv", "false"),
            Map.entry("physical_screen_height", "158"),
            Map.entry("ajax_support_javascript", "true"),
            Map.entry("can_assign_phone_number", "true"),
            Map.entry("is_ott", "false"),
            Map.entry("is_tablet", "false"),
            Map.entry("physical_form_factor", "phone_phablet"),
            Map.entry("xhtml_support_level", "4")
    );
    static final Map<String, String> VIRTUAL_CAPABILITIES = Map.of(
            "advertised_device_os", "Android",
            "advertised_device_os_version", "15",
            "pixel_density", "481",
            "is_phone", "true",
            "is_mobile", "true",
            "is_full_desktop", "false",
            "form_factor", "Smartphone",
            "is_android", "true",
            "is_ios", "false",
            "complete_device_name", "Google Pixel 9 Pro XL"
    );

    static final Set<String> CAPABILITIES_KEYS = new HashSet<>(CAPABILITIES.keySet());
    static final Set<String> VIRTUAL_CAPABILITIES_KEYS = new HashSet<>(VIRTUAL_CAPABILITIES.keySet());

    @Override
    public Set<String> getAllCapabilities() {
        return CAPABILITIES_KEYS;
    }

    @Override
    public Set<String> getAllVirtualCapabilities() {
        return VIRTUAL_CAPABILITIES_KEYS;
    }

    @Override
    public void load() {
    }

    @Override
    public void setCacheProvider(CacheProvider cacheProvider) {
    }

    @Override
    public Device getDeviceById(String deviceId) {
        return mockDevice();
    }

    @Override
    public Device getDeviceForRequest(Map<String, String> headers) {
        return mockDevice();
    }

    private Device mockDevice() {
        return new Device() {
            @Override
            public String getId() {
                return "google_pixel_9_pro_xl_ver1_suban150";
            }

            @Override
            public MatchType getMatchType() {
                return MatchType.conclusive;
            }

            @Override
            public String getCapability(String name) throws CapabilityNotDefinedException {
                if (CAPABILITIES.containsKey(name)) {
                    return CAPABILITIES.get(name);
                } else {
                    throw new CapabilityNotDefinedException(
                            "Capability: " + name + " is not defined in WURFL");
                }
            }

            @Override
            public String getVirtualCapability(String name) throws VirtualCapabilityNotDefinedException,
                    CapabilityNotDefinedException {
                if (VIRTUAL_CAPABILITIES.containsKey(name)) {
                    return VIRTUAL_CAPABILITIES.get(name);
                } else {
                    throw new VirtualCapabilityNotDefinedException(
                            "Virtual Capability: " + name + " is not defined in WURFL");
                }
            }

            @Override
            public int getVirtualCapabilityAsInt(String s) throws VirtualCapabilityNotDefinedException,
                    CapabilityNotDefinedException, NumberFormatException {
                return 0;
            }

            @Override
            public boolean getVirtualCapabilityAsBool(String vcapName) throws VirtualCapabilityNotDefinedException,
                    CapabilityNotDefinedException, NumberFormatException {
                return Boolean.parseBoolean(getVirtualCapability(vcapName));
            }

            @Override
            public String getWURFLUserAgent() {
                return "";
            }

            @Override
            public int getCapabilityAsInt(String capName) throws CapabilityNotDefinedException, NumberFormatException {
                return 0;
            }

            @Override
            public boolean getCapabilityAsBool(String capName) throws CapabilityNotDefinedException,
                    NumberFormatException {
                return Boolean.parseBoolean(getCapability(capName));
            }
        };
    }
}
