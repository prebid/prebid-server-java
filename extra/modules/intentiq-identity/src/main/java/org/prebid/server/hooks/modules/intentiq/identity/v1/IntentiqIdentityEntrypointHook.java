package org.prebid.server.hooks.modules.intentiq.identity.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.entrypoint.EntrypointPayloadImpl;
import org.prebid.server.hooks.modules.intentiq.identity.model.RequestPartnerContext;
import org.prebid.server.hooks.modules.intentiq.identity.v1.core.PartnerIdResolver;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.entrypoint.EntrypointHook;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.json.JacksonMapper;

import java.util.Objects;

/**
 * Runs at the {@code entrypoint} stage, on the raw request body. Prebid.js puts {@code s2sConfig.extPrebid} into
 * {@code ext.prebid} of the request it sends to PBS only, so a host can send the partner id there as
 * {@code ext.prebid.intentiq.partnerId}. PBS parses {@code ext.prebid} into a fixed class and drops fields it does
 * not know, so this is the only stage that can read it. The hook keeps the requested partner id in the module
 * context and takes {@code ext.prebid.intentiq} out of the body. It does no I/O.
 */
public class IntentiqIdentityEntrypointHook implements EntrypointHook {

    public static final String CODE = "intentiq-identity-entrypoint-hook";

    private static final String EXT = "ext";
    private static final String PREBID = "prebid";

    private final JacksonMapper mapper;

    public IntentiqIdentityEntrypointHook(JacksonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Future<InvocationResult<EntrypointPayload>> call(EntrypointPayload entrypointPayload,
                                                            InvocationContext invocationContext) {
        final String body = entrypointPayload.body();
        // Cheap check first, so requests without the field are never parsed twice
        final ObjectNode prebid = StringUtils.contains(body, PartnerIdResolver.EXT_FIELD) ? extPrebid(body) : null;
        if (prebid == null || !prebid.has(PartnerIdResolver.EXT_FIELD)) {
            return Future.succeededFuture(noAction());
        }

        final String partnerId = PartnerIdResolver.fromNode(prebid.get(PartnerIdResolver.EXT_FIELD));
        return Future.succeededFuture(InvocationResultImpl.<EntrypointPayload>builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.update)
                .payloadUpdate(payload -> EntrypointPayloadImpl.of(
                        payload.queryParams(), payload.headers(), withoutField(payload.body())))
                .moduleContext(new RequestPartnerContext(partnerId))
                .build());
    }

    private ObjectNode extPrebid(String body) {
        try {
            final JsonNode prebid = mapper.mapper().readTree(body).path(EXT).path(PREBID);
            return prebid instanceof ObjectNode node ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String withoutField(String body) {
        try {
            final JsonNode root = mapper.mapper().readTree(body);
            if (root.path(EXT).path(PREBID) instanceof ObjectNode prebid) {
                prebid.remove(PartnerIdResolver.EXT_FIELD);
            }

            return mapper.mapper().writeValueAsString(root);
        } catch (Exception e) {
            return body;
        }
    }

    private static InvocationResult<EntrypointPayload> noAction() {
        return InvocationResultImpl.<EntrypointPayload>builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.no_action)
                .build();
    }

    @Override
    public String code() {
        return CODE;
    }
}
