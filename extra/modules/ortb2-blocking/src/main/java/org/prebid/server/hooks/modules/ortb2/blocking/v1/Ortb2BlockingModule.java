package org.prebid.server.hooks.modules.ortb2.blocking.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class Ortb2BlockingModule implements Module {

    public static final String CODE = "ortb2-blocking";

    private final List<? extends Hook<?, ? extends InvocationContext>> hooks;

    public Ortb2BlockingModule(BidderCatalog bidderCatalog, ObjectMapper mapper) {
        Objects.requireNonNull(bidderCatalog);
        Objects.requireNonNull(mapper);

        hooks = List.of(
                new Ortb2BlockingBidderRequestHook(bidderCatalog),
                new Ortb2BlockingRawBidderResponseHook(mapper));
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Collection<? extends Hook<?, ? extends InvocationContext>> hooks() {
        return hooks;
    }
}
