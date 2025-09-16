package org.prebid.server.auction.gpp;

import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.GppContextWrapper;
import org.prebid.server.auction.gpp.processor.GppContextProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GppService {

    private final List<GppContextProcessor> processors;

    public GppService(List<GppContextProcessor> processors) {
        this.processors = Objects.requireNonNull(processors);
    }

    public GppContextWrapper processContext(GppContextWrapper initialGppContextWrapper) {
        GppContext localContext = initialGppContextWrapper.getGppContext();
        final List<String> errors = new ArrayList<>(initialGppContextWrapper.getErrors());

        for (GppContextProcessor processor : processors) {
            final GppContextWrapper gppContextWrapper = processor.process(localContext);

            localContext = gppContextWrapper.getGppContext();
            errors.addAll(gppContextWrapper.getErrors());
        }

        return GppContextWrapper.of(localContext, errors);
    }
}
