package org.prebid.server.hooks.modules.intentiq.identity.v1;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.execution.v1.entrypoint.EntrypointPayloadImpl;
import org.prebid.server.hooks.modules.intentiq.identity.model.RequestPartnerContext;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.model.CaseInsensitiveMultiMap;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class IntentiqIdentityEntrypointHookTest {

    @Mock
    private InvocationContext invocationContext;

    private final IntentiqIdentityEntrypointHook target =
            new IntentiqIdentityEntrypointHook(new JacksonMapper(ObjectMapperProvider.mapper()));

    @Test
    public void codeShouldReturnExpectedValue() {
        assertThat(target.code()).isEqualTo("intentiq-identity-entrypoint-hook");
    }

    @Test
    public void callShouldRemoveExtPrebidIntentiqAndKeepThePartnerIdInTheModuleContext() throws Exception {
        // given
        final EntrypointPayload payload = payload("""
                {"id":"r1","imp":[{"id":"1","bidfloor":0.1}],
                 "ext":{"prebid":{"debug":true,"intentiq":{"partnerId":" 777 "}},"other":"keep"}}""");

        // when
        final InvocationResult<EntrypointPayload> result = target.call(payload, invocationContext).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
        assertThat(result.moduleContext()).isEqualTo(new RequestPartnerContext("777"));
        final JsonNode body = ObjectMapperProvider.mapper().readTree(result.payloadUpdate().apply(payload).body());
        assertThat(body.path("ext").path("prebid").has("intentiq")).isFalse();
        assertThat(body.path("ext").path("prebid").path("debug").asBoolean()).isTrue();
        assertThat(body.path("ext").path("other").asText()).isEqualTo("keep");
        assertThat(body.path("imp").get(0).path("bidfloor").asDouble()).isEqualTo(0.1);
    }

    @Test
    public void callShouldAcceptANumericPartnerId() {
        // when
        final InvocationResult<EntrypointPayload> result = target.call(
                payload("{\"ext\":{\"prebid\":{\"intentiq\":{\"partnerId\":777}}}}"), invocationContext).result();

        // then
        assertThat(result.moduleContext()).isEqualTo(new RequestPartnerContext("777"));
    }

    @Test
    public void callShouldRemoveAnExtPrebidIntentiqWithoutAUsablePartnerId() throws Exception {
        // given
        final EntrypointPayload payload = payload("{\"ext\":{\"prebid\":{\"intentiq\":{\"partnerId\":{}}}}}");

        // when
        final InvocationResult<EntrypointPayload> result = target.call(payload, invocationContext).result();

        // then
        assertThat(result.moduleContext()).isEqualTo(new RequestPartnerContext(null));
        assertThat(ObjectMapperProvider.mapper().readTree(result.payloadUpdate().apply(payload).body())
                .path("ext").path("prebid").has("intentiq")).isFalse();
    }

    @Test
    public void callShouldDoNothingWithoutExtPrebidIntentiq() {
        assertNoAction("{\"ext\":{\"intentiq\":{\"partnerId\":\"777\"}}}");
        assertNoAction("{\"user\":{\"eids\":[{\"source\":\"intentiq.com\"}]}}");
        assertNoAction("{\"id\":\"r1\"}");
    }

    @Test
    public void callShouldDoNothingForAnEmptyOrInvalidBody() {
        assertNoAction(null);
        assertNoAction("");
        assertNoAction("not json intentiq");
    }

    private void assertNoAction(String body) {
        final InvocationResult<EntrypointPayload> result = target.call(payload(body), invocationContext).result();
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        assertThat(result.payloadUpdate()).isNull();
        assertThat(result.moduleContext()).isNull();
    }

    private static EntrypointPayload payload(String body) {
        return EntrypointPayloadImpl.of(CaseInsensitiveMultiMap.empty(), CaseInsensitiveMultiMap.empty(), body);
    }
}
