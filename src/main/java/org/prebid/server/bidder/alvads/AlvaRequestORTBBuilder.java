package org.prebid.server.bidder.alvads;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import org.prebid.server.json.JacksonMapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlvaRequestORTBBuilder {

    private String id;
    private List<AlvadsRequestORTB.AlvaAdsImp> imp;
    private Device device;
    private User user;
    private Regs regs;
    private AlvadsRequestORTB.AlvaAdsSite site;
    private final BidRequest bidRequest;
    private final AlvadsImpExt ext;
    private final JacksonMapper mapper;
    private final Imp bidImp;

    public AlvaRequestORTBBuilder(BidRequest bidRequest, Imp imp, AlvadsImpExt ext, JacksonMapper mapper) {
        this.bidRequest = bidRequest;
        this.bidImp = imp;
        this.ext = ext;
        this.mapper = mapper;
    }

    public AlvaRequestORTBBuilder setId(String id) {
        this.id = id;
        return this;
    }

    public AlvaRequestORTBBuilder setImp(List<AlvadsRequestORTB.AlvaAdsImp> imp) {
        this.imp = imp;
        return this;
    }

    public AlvaRequestORTBBuilder setDevice(Device device) {
        this.device = device;
        return this;
    }

    public AlvaRequestORTBBuilder setUser(User user) {
        this.user = user;
        return this;
    }

    public AlvaRequestORTBBuilder setRegs(Regs regs) {
        this.regs = regs;
        return this;
    }

    public AlvaRequestORTBBuilder setSite(AlvadsRequestORTB.AlvaAdsSite site) {
        this.site = site;
        return this;
    }

    public AlvadsRequestORTB build() {
        final AlvadsRequestORTB request = new AlvadsRequestORTB();
        request.setId(this.id != null ? this.id : this.bidRequest.getId());
        if (this.imp == null) {
            final AlvadsRequestORTB.AlvaAdsImp alvaAdsImp = new AlvadsRequestORTB.AlvaAdsImp();
            alvaAdsImp.setId(this.bidImp.getId());
            final Map<String, Object> banner = new HashMap<>();
            banner.put("w", bidImp.getBanner().getW());
            banner.put("h", bidImp.getBanner().getH());
            alvaAdsImp.setBanner(banner);
            alvaAdsImp.setTagid(bidImp.getTagid());
            alvaAdsImp.setBidfloor(bidImp.getBidfloor());
            this.imp = Collections.singletonList(alvaAdsImp);
        }
        request.setImp(this.imp);
        request.setDevice(this.device != null ? this.device : bidRequest.getDevice());
        request.setUser(this.user != null ? this.user : bidRequest.getUser());
        request.setRegs(this.regs != null ? this.regs : bidRequest.getRegs());
        if (this.site == null) {
            this.site = new AlvadsRequestORTB.AlvaAdsSite();
            site.setPage(bidRequest.getSite().getPage());
            site.setRef(bidRequest.getSite().getPage());
            final Map<String, Object> publisher = new HashMap<>();
            publisher.put("id", ext.getPublisherUniqueId());
            site.setPublisher(publisher);
        }
        request.setSite(this.site);
        return request;
    }

    public AlvadsRequestORTB buildVideo() {
        final AlvadsRequestORTB request = new AlvadsRequestORTB();
        request.setId(this.id != null ? this.id : this.bidRequest.getId());
        if (this.imp == null) {
            final AlvadsRequestORTB.AlvaAdsImp alvaAdsImp = new AlvadsRequestORTB.AlvaAdsImp();
            alvaAdsImp.setId(this.bidImp.getId());
            final Map<String, Object> video = new HashMap<>();
            video.put("w", bidImp.getVideo().getW());
            video.put("h", bidImp.getVideo().getH());
            alvaAdsImp.setVideo(video);
            alvaAdsImp.setTagid(bidImp.getTagid());
            alvaAdsImp.setBidfloor(bidImp.getBidfloor());
            this.imp = Collections.singletonList(alvaAdsImp);
        }
        request.setImp(this.imp);
        request.setDevice(this.device != null ? this.device : bidRequest.getDevice());
        request.setUser(this.user != null ? this.user : bidRequest.getUser());
        request.setRegs(this.regs != null ? this.regs : bidRequest.getRegs());
        if (this.site == null) {
            this.site = new AlvadsRequestORTB.AlvaAdsSite();
            site.setPage(bidRequest.getSite().getPage());
            site.setRef(bidRequest.getSite().getPage());
            final Map<String, Object> publisher = new HashMap<>();
            publisher.put("id", ext.getPublisherUniqueId());
            site.setPublisher(publisher);
        }
        request.setSite(this.site);
        return request;
    }
}
