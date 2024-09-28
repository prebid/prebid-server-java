package org.prebid.server.hooks.modules.pb.response.correction.core.correction.appvideohtml;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.hooks.modules.pb.response.correction.core.config.model.AppVideoHtmlConfig;
import org.prebid.server.hooks.modules.pb.response.correction.core.config.model.Config;
import org.prebid.server.hooks.modules.pb.response.correction.core.correction.Correction;
import org.prebid.server.hooks.modules.pb.response.correction.core.correction.CorrectionProducer;

public class AppVideoHtmlCorrectionProducer implements CorrectionProducer {

    private final AppVideoHtmlCorrection correctionInstance;

    public AppVideoHtmlCorrectionProducer(AppVideoHtmlCorrection correction) {
        this.correctionInstance = correction;
    }

    @Override
    public boolean shouldProduce(Config config, BidRequest bidRequest) {
        final AppVideoHtmlConfig appVideoHtmlConfig = config.getAppVideoHtmlConfig();
        final boolean enabled = appVideoHtmlConfig != null && appVideoHtmlConfig.isEnabled();
        return enabled && bidRequest.getApp() != null;
    }

    @Override
    public Correction produce() {
        return correctionInstance;
    }
}
