package org.prebid.server.bidder.alvads.model;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder(toBuilder = true)
public class AlvadsRequestOrtb {

    private String id;
    private List<AlvaAdsImp> imp;
    private Device device;
    private User user;
    private Regs regs;
    private AlvaAdsSite site;
}
