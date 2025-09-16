package org.prebid.server.bidder.alvads.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
public class AlvadsRequestORTB {
    private String id;
    private List<AlvaAdsImp> imp;
    private Device device;
    private User user;
    private Regs regs;
    private AlvaAdsSite site;
}
