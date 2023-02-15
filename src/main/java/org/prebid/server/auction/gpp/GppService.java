package org.prebid.server.auction.gpp;

import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.processor.GppContextProcessor;

import java.util.List;
import java.util.Objects;

public class GppService {

    private final List<GppContextProcessor> processors;

    public GppService(List<GppContextProcessor> processors) {
        this.processors = Objects.requireNonNull(processors);
    }

    public GppContext processContext(GppContext gppContext) {
        GppContext localContext = gppContext;
        for (GppContextProcessor processor : processors) {
            localContext = processor.process(localContext);
        }

        return localContext;
    }
}
