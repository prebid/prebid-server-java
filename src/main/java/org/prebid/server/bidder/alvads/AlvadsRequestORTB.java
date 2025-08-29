package org.prebid.server.bidder.alvads;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class AlvadsRequestORTB {

    private String id;
    private List<AlvaAdsImp> imp;
    private Device device;
    private User user;
    private Regs regs;
    private AlvaAdsSite site;

    @Data
    public static class AlvaAdsSite {
        private String page;
        private String ref;
        private Map<String, Object> publisher;

        public AlvaAdsSite() {
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AlvaAdsImp {
        private String id;
        private Map<String, Object> banner;
        private Map<String, Object> video;
        private String tagid;
        private BigDecimal bidfloor;
    }
}
