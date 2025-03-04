package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.mock;

import com.scientiamobile.wurfl.core.exc.CapabilityNotDefinedException;
import com.scientiamobile.wurfl.core.exc.VirtualCapabilityNotDefinedException;
import com.scientiamobile.wurfl.core.matchers.MatchType;
import lombok.Builder;

import java.util.Map;

@Builder
public class WURFLDeviceMock implements com.scientiamobile.wurfl.core.Device {

    private Map<String, String> capabilities;
    private String id;
    private Map<String, String> virtualCapabilities;

    @Override
    public MatchType getMatchType() {
        return MatchType.conclusive;
    }

    @Override
    public String getVirtualCapability(String vcapName) throws VirtualCapabilityNotDefinedException,
            CapabilityNotDefinedException {

        if (!virtualCapabilities.containsKey(vcapName)) {
            throw new VirtualCapabilityNotDefinedException(vcapName);
        }

        return virtualCapabilities.get(vcapName);
    }

    @Override
    public int getVirtualCapabilityAsInt(String s) throws VirtualCapabilityNotDefinedException,
            CapabilityNotDefinedException, NumberFormatException {
        return 0;
    }

    @Override
    public boolean getVirtualCapabilityAsBool(String vcapName) throws VirtualCapabilityNotDefinedException,
            CapabilityNotDefinedException, NumberFormatException {

        if (vcapName.equals("is_phone") || vcapName.equals("is_full_desktop") || vcapName.equals("is_connected_tv")
                || vcapName.equals("is_mobile") || vcapName.equals("is_tablet")) {
            return Boolean.parseBoolean(getVirtualCapability(vcapName));
        }

        return false;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getWURFLUserAgent() {
        return "";
    }

    @Override
    public String getCapability(String capName) throws CapabilityNotDefinedException {

        if (!capabilities.containsKey(capName)) {
            throw new CapabilityNotDefinedException(capName);
        }

        return capabilities.get(capName);

    }

    @Override
    public int getCapabilityAsInt(String capName) throws CapabilityNotDefinedException, NumberFormatException {
        return switch (capName) {
            case "resolution_height", "resolution_width" -> Integer.parseInt(capabilities.get(capName));
            default -> 0;
        };
    }

    @Override
    public boolean getCapabilityAsBool(String capName) throws CapabilityNotDefinedException, NumberFormatException {
        return switch (capName) {
            case "ajax_support_javascript", "is_connected_tv", "is_ott", "is_tablet", "is_mobile" ->
                    Boolean.parseBoolean(getCapability(capName));
            default -> false;
        };
    }

    public Map<String, String> getCapabilities() {
        return Map.of();
    }

    public Map<String, String> getVirtualCapabilities() {
        return Map.of();
    }

    public boolean isActualDeviceRoot() {
        return true;
    }

    public String getDeviceRootId() {
        return "";
    }

    public static class WURFLDeviceMockFactory {

        public static com.scientiamobile.wurfl.core.Device mockIPhone() {

            return builder().capabilities(Map.of(
                            "brand_name", "Apple",
                            "model_name", "iPhone",
                            "ajax_support_javascript", "true",
                            "density_class", "1.0",
                            "is_connected_tv", "false",
                            "is_ott", "false",
                            "is_tablet", "false",
                            "resolution_height", "1440",
                            "resolution_width", "3200"
                    )).virtualCapabilities(
                            Map.of("advertised_device_os", "iOS",
                                    "advertised_device_os_version", "17.1",
                                    "complete_device_name", "Apple iPhone",
                                    "is_full_desktop", "false",
                                    "is_mobile", "true",
                                    "is_phone", "true",
                                    "form_factor", "Smartphone",
                                    "pixel_density", "515"))
                    .id("apple_iphone_ver1")
                    .build();
        }

        public static com.scientiamobile.wurfl.core.Device mockOttDevice() {

            return builder().capabilities(Map.of(
                            "brand_name", "Diyomate",
                            "model_name", "A6",
                            "ajax_support_javascript", "true",
                            "density_class", "1.5",
                            "is_connected_tv", "false",
                            "is_ott", "true",
                            "is_tablet", "false",
                            "resolution_height", "1080",
                            "resolution_width", "1920"
                    )).virtualCapabilities(
                            Map.of("advertised_device_os", "Android",
                                    "advertised_device_os_version", "4.0",
                                    "complete_device_name", "Diyomate A6",
                                    "is_full_desktop", "false",
                                    "is_mobile", "false",
                                    "is_phone", "false",
                                    "form_factor", "Smart-TV",
                                    "pixel_density", "69"))
                    .id("diyomate_a6_ver1")
                    .build();
        }

        public static com.scientiamobile.wurfl.core.Device mockMobileUndefinedDevice() {

            return builder().capabilities(Map.of(
                            "brand_name", "TestUnd",
                            "model_name", "U1",
                            "ajax_support_javascript", "false",
                            "density_class", "1.0",
                            "is_connected_tv", "false",
                            "is_ott", "false",
                            "is_tablet", "false",
                            "resolution_height", "128",
                            "resolution_width", "128"
                    )).virtualCapabilities(
                            Map.of("advertised_device_os", "TestOS",
                                    "advertised_device_os_version", "1.0",
                                    "complete_device_name", "TestUnd U1",
                                    "is_full_desktop", "false",
                                    "is_mobile", "true",
                                    "is_phone", "false",
                                    "form_factor", "Test-non-phone",
                                    "pixel_density", "69"))
                    .build();
        }

        public static com.scientiamobile.wurfl.core.Device mockUnknownDevice() {

            return builder().capabilities(Map.of(
                            "brand_name", "TestUnd",
                            "model_name", "U1",
                            "ajax_support_javascript", "false",
                            "density_class", "1.0",
                            "is_connected_tv", "false",
                            "is_ott", "false",
                            "is_tablet", "false",
                            "resolution_height", "128",
                            "resolution_width", "128"
                    )).virtualCapabilities(
                            Map.of("advertised_device_os", "TestOS",
                                    "advertised_device_os_version", "1.0",
                                    "complete_device_name", "TestUnd U1",
                                    "is_full_desktop", "false",
                                    "is_mobile", "false",
                                    "is_phone", "false",
                                    "form_factor", "Test-unknown",
                                    "pixel_density", "69"))
                    .build();
        }

        public static com.scientiamobile.wurfl.core.Device mockDesktop() {

            return builder().capabilities(Map.of(
                            "brand_name", "TestDesktop",
                            "model_name", "D1",
                            "ajax_support_javascript", "true",
                            "density_class", "1.5",
                            "is_connected_tv", "false",
                            "is_ott", "false",
                            "is_tablet", "false",
                            "resolution_height", "1080",
                            "resolution_width", "1920"
                    )).virtualCapabilities(
                            Map.of("advertised_device_os", "Windows",
                                    "advertised_device_os_version", "10",
                                    "complete_device_name", "TestDesktop D1",
                                    "is_full_desktop", "true",
                                    "is_mobile", "false",
                                    "is_phone", "false",
                                    "form_factor", "Desktop",
                                    "pixel_density", "300"))
                    .build();
        }

        public static com.scientiamobile.wurfl.core.Device mockConnectedTv() {

            return builder().capabilities(Map.of(
                            "brand_name", "TestConnectedTv",
                            "model_name", "C1",
                            "ajax_support_javascript", "true",
                            "density_class", "1.5",
                            "is_connected_tv", "true",
                            "is_ott", "false",
                            "is_tablet", "false",
                            "resolution_height", "1080",
                            "resolution_width", "1920"
                    )).virtualCapabilities(
                            Map.of("advertised_device_os", "WebOS",
                                    "advertised_device_os_version", "4",
                                    "complete_device_name", "TestConnectedTV C1",
                                    "is_full_desktop", "false",
                                    "is_mobile", "false",
                                    "is_phone", "false",
                                    "form_factor", "Smart-TV",
                                    "pixel_density", "200"))
                    .build();
        }

        public static com.scientiamobile.wurfl.core.Device mockTablet() {

            return builder().capabilities(Map.of(
                            "brand_name", "Samsung",
                            "model_name", "Galaxy Tab S9+",
                            "ajax_support_javascript", "true",
                            "density_class", "1.5",
                            "is_connected_tv", "false",
                            "is_ott", "false",
                            "is_tablet", "true",
                            "resolution_height", "1752",
                            "resolution_width", "2800"
                    )).virtualCapabilities(
                            Map.of("advertised_device_os", "Android",
                                    "advertised_device_os_version", "13",
                                    "complete_device_name", "Samsung Galaxy Tab S9+",
                                    "is_full_desktop", "false",
                                    "is_mobile", "false",
                                    "is_phone", "false",
                                    "form_factor", "Tablet",
                                    "pixel_density", "274"))
                    .build();
        }

    }
}
