package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.User;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.huaweiads.model.request.Device;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtUserDataDeviceIdHuaweiAds;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class HuaweiDeviceBuilder {

    private static final TypeReference<ExtUserDataDeviceIdHuaweiAds> EXT_USER_TYPE_REFERENCE = new TypeReference<>() {
    };

    private static final String DEFAULT_MODEL_NAME = "HUAWEI";

    private final JacksonMapper mapper;

    public HuaweiDeviceBuilder(JacksonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    public Device build(com.iab.openrtb.request.Device device, User user, String countryCode) throws PreBidException {
        final Device deviceId = makeDeviceWithDeviceId(device, user);
        if (device == null) {
            return deviceId;
        }

        final String tracking = device.getDnt() != null ? String.valueOf(1 - device.getDnt()) : null;
        return deviceId.toBuilder()
                .type(device.getDevicetype())
                .userAgent(device.getUa())
                .os(device.getOs())
                .version(device.getOsv())
                .maker(device.getMake())
                .model(HuaweiUtils.getIfNotBlank(device.getModel()).orElse(DEFAULT_MODEL_NAME))
                .height(device.getH())
                .width(device.getW())
                .language(device.getLanguage())
                .pxratio(device.getPxratio())
                .belongCountry(countryCode)
                .localeCountry(countryCode)
                .ip(device.getIp())
                .isTrackingEnabled(tracking != null && StringUtils.isNotBlank(deviceId.getOaid()) ? tracking : null)
                .gaidTrackingEnabled(tracking != null && StringUtils.isNotBlank(deviceId.getGaid()) ? tracking : null)
                .build();
    }

    // getDeviceID include oaid gaid imei. In prebid mobile, use TargetingParams.addUserData("imei", "imei-test");
    // When ifa: gaid exists, other device id can be passed by TargetingParams.addUserData("oaid", "oaid-test");
    private Device makeDeviceWithDeviceId(com.iab.openrtb.request.Device device, User user) {
        final Optional<String> deviceIfa = Optional.ofNullable(device)
                .flatMap(dev -> HuaweiUtils.getIfNotBlank(dev.getIfa()));
        if (user == null || user.getExt() == null) {
            return deviceIfa.map(gaid -> Device.builder().gaid(gaid).clientTime(ClientTimeFormatter.now()).build())
                    .orElseThrow(() -> new PreBidException("getDeviceID: openRTBRequest.User.Ext is nil "
                            + "and device.Gaid is not specified."));

        }

        final ExtUserDataDeviceIdHuaweiAds userData = parseUserExtData(user.getExt());
        final boolean isImeiEmpty = userData == null || CollectionUtils.isEmpty(userData.getImei());
        final boolean isGaidEmpty = userData == null || CollectionUtils.isEmpty(userData.getGaid());
        final boolean isOaidEmpty = userData == null || CollectionUtils.isEmpty(userData.getOaid());

        if (isImeiEmpty && isOaidEmpty && isGaidEmpty && deviceIfa.isEmpty()) {
            throw new PreBidException("getDeviceID: Imei, Oaid, Gaid are all empty.");
        }

        final String gaid = isGaidEmpty
                ? deviceIfa.orElseThrow(() -> new PreBidException("getDeviceID: openRTBRequest.User.Ext is nil "
                + "and device.Gaid is not specified."))
                : userData.getGaid().get(0);
        final String oaid = isOaidEmpty ? null : userData.getOaid().get(0);
        final String imei = isImeiEmpty ? null : userData.getImei().get(0);
        final String clientTime = Optional.ofNullable(userData)
                .map(ExtUserDataDeviceIdHuaweiAds::getClientTime)
                .map(HuaweiDeviceBuilder::formatClientTime)
                .orElseGet(ClientTimeFormatter::now);

        return Device.builder()
                .clientTime(clientTime)
                .oaid(oaid)
                .gaid(gaid)
                .imei(imei)
                .build();
    }

    private ExtUserDataDeviceIdHuaweiAds parseUserExtData(ExtUser extUser) {
        try {
            return mapper.mapper().convertValue(extUser.getData(), EXT_USER_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private static String formatClientTime(List<String> clientTimes) {
        return CollectionUtils.isEmpty(clientTimes) ? null : ClientTimeFormatter.format(clientTimes.get(0));
    }

}
