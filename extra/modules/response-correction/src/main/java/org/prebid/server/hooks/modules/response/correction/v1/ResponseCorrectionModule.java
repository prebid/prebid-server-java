package org.prebid.server.hooks.modules.response.correction.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.prebid.server.hooks.modules.response.correction.core.CorrectionsProvider;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;

import java.util.Collection;
import java.util.Collections;

public class ResponseCorrectionModule implements Module {

    public static final String CODE = "pb-response-correction";

    private final Collection<? extends Hook<?, ? extends InvocationContext>> hooks;

    public ResponseCorrectionModule(CorrectionsProvider correctionsProvider, ObjectMapper mapper) {
        this.hooks = Collections.singleton(
                new ResponseCorrectionAllProcessedBidResponsesHook(correctionsProvider, mapper));
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
