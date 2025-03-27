package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Uid;
import com.iab.openrtb.request.User;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.optable.targeting.model.OS;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.ExtUserOptable;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.util.List;
import java.util.Optional;

public class IdsResolver {

    private final Optional<BidRequest> bidRequest;
    private final ObjectMapper objectMapper;
    private final Optional<ExtUser> extUser;
    private final Optional<ExtUserOptable> extUserOptable;
    private final Optional<Device> device;

    public static IdsResolver of(ObjectMapper objectMapper, BidRequest bidRequest) {
        return new IdsResolver(objectMapper, bidRequest);
    }

    private IdsResolver(ObjectMapper objectMapper, BidRequest bidRequest) {
        this.objectMapper = objectMapper;
        this.bidRequest = Optional.ofNullable(bidRequest);
        this.extUser = getExtUser();
        this.extUserOptable = getExtUserOptable();
        this.device = this.bidRequest.map(BidRequest::getDevice);
    }

    public String getEmail() {
        return extUserOptable.map(ExtUserOptable::getEmail).orElse(null);
    }

    public String getPhone() {
        return extUserOptable.map(ExtUserOptable::getPhone).orElse(null);
    }

    public String getZip() {
        return extUserOptable.map(ExtUserOptable::getZip).orElse(null);
    }

    public String getOptableVID() {
        return extUserOptable.map(ExtUserOptable::getVid).orElse(null);
    }

    public String getDeviceIfa(OS os) {
        final String deviceOS = getDeviceOS();
        final Integer deviceLmt = getDeviceLmt();

        if (StringUtils.isEmpty(deviceOS)) {
            return null;
        }

        if (deviceOS.contains(os.value.toLowerCase()) && !(deviceLmt != null && deviceLmt.equals(1))) {
            return device.map(Device::getIfa).orElse(null);
        }

        return null;
    }

    public String getID5() {
        return getUid("id5-sync.com");
    }

    public String getUtiq() {
        return getUid("utiq.com");
    }

    public String getNetId() {
        return getUid("netid.de");
    }

    public List<Eid> getEIDs() {
        return bidRequest.map(BidRequest::getUser).map(User::getEids).orElse(List.of());
    }

    private Optional<ExtUser> getExtUser() {
        return bidRequest.map(BidRequest::getUser).map(User::getExt);
    }

    private Integer getDeviceLmt() {
        return device.map(Device::getLmt).orElse(null);
    }

    private String getDeviceOS() {
        return device.map(Device::getOs).map(String::toLowerCase).orElse(null);
    }

    private String getUid(String source) {
        return getEIDs()
                .stream()
                .filter(it -> it.getSource().equals(source))
                .findFirst()
                .map(Eid::getUids)
                .flatMap(it -> it.stream().findFirst())
                .map(Uid::getId)
                .orElse(null);
    }

    private Optional<ExtUserOptable> getExtUserOptable() {
        return extUser.map(it -> it.getProperty("optable"))
                .map(it -> {
                    try {
                        return objectMapper.treeToValue(it, ExtUserOptable.class);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
