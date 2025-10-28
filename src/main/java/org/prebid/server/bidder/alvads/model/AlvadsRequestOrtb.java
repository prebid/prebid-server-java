package org.prebid.server.bidder.alvads.model;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
public class AlvadsRequestOrtb {

    String id;

    List<AlvaAdsImp> imp;

    Device device;

    User user;

    Regs regs;

    AlvaAdsSite site;
}
