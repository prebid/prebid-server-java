package org.prebid.server.auction.gpp;

import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.processor.GppContextProcessor;
import org.prebid.server.auction.gpp.processor.tcfeuv2.TcfEuV2ContextProcessor;
import org.prebid.server.auction.gpp.processor.uspv1.UspV1ContextProcessor;

import java.util.List;

public class GppService {

    private final List<GppContextProcessor> processors;

    public GppService() {
        this.processors = List.of(
                new TcfEuV2ContextProcessor(),
                new UspV1ContextProcessor());
    }

    public GppContext processContext(GppContext gppContext) {
        GppContext localContext = gppContext;
        for (GppContextProcessor processor : processors) {
            localContext = processor.process(localContext);
        }

        return localContext;
    }
}
