package org.prebid.server.hooks.modules.pb.request.correction.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.prebid.server.hooks.modules.pb.request.correction.core.RequestCorrectionProvider;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;

import java.util.Collection;
import java.util.Collections;

public class RequestCorrectionModule implements Module {

    public static final String CODE = "pb-request-correction";

    private final Collection<? extends Hook<?, ? extends InvocationContext>> hooks;

    public RequestCorrectionModule(RequestCorrectionProvider requestCorrectionProvider, ObjectMapper mapper) {
        this.hooks = Collections.singleton(
                new RequestCorrectionProcessedAuctionHook(requestCorrectionProvider, mapper));
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
