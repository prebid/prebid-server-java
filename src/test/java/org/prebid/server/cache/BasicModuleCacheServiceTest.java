package org.prebid.server.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.cache.proto.request.module.ModuleCacheRequest;
import org.prebid.server.cache.proto.request.module.ModuleCacheType;
import org.prebid.server.cache.proto.response.module.ModuleCacheResponse;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.net.MalformedURLException;
import java.net.URL;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class BasicModuleCacheServiceTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpClient httpClient;

    private BasicModuleCacheService target;

    @Before
    public void setUp() throws MalformedURLException, JsonProcessingException {
        target = new BasicModuleCacheService(
                httpClient,
                new URL("http://cache-service/cache"),
                "pbc-api-key",
                10,
                jacksonMapper);

        given(httpClient.post(anyString(), any(), any(), anyLong())).willReturn(Future.succeededFuture(
                HttpClientResponse.of(200, null, "someBody")));
        given(httpClient.get(anyString(), any(), anyLong())).willReturn(Future.succeededFuture(
                HttpClientResponse.of(200, null, mapper.writeValueAsString(
                        ModuleCacheResponse.of("some-key", ModuleCacheType.JSON, "some-value")))));
    }

    @Test
    public void storeModuleEntryShouldStoreExpectedKey() {
        // when
        target.storeModuleEntry("some-key",
                "some-value",
                ModuleCacheType.TEXT,
                12,
                "some-application",
                "some-module-code");

        // then
        final ModuleCacheRequest result = captureModuleCacheRequest();
        assertThat(result.getKey()).isEqualTo("module.some-module-code.some-key");
    }

    @Test
    public void storeModuleEntryShouldStoreExpectedValue() {
        // when
        target.storeModuleEntry("some-key",
                "some-value",
                ModuleCacheType.TEXT,
                12,
                "some-application",
                "some-module-code");

        // then
        final ModuleCacheRequest result = captureModuleCacheRequest();
        assertThat(result.getValue()).isEqualTo("c29tZS12YWx1ZQ==");
    }

    @Test
    public void storeModuleEntryShouldStoreExpectedApplication() {
        // when
        target.storeModuleEntry("some-key",
                "some-value",
                ModuleCacheType.TEXT,
                12,
                "some-application",
                "some-module-code");

        // then
        final ModuleCacheRequest result = captureModuleCacheRequest();
        assertThat(result.getApplication()).isEqualTo("some-application");
    }

    @Test
    public void storeModuleEntryShouldStoreExpectedMediaType() {
        // when
        target.storeModuleEntry("some-key",
                "some-value",
                ModuleCacheType.TEXT,
                12,
                "some-application",
                "some-module-code");

        // then
        final ModuleCacheRequest result = captureModuleCacheRequest();
        assertThat(result.getType()).isEqualTo(ModuleCacheType.TEXT);
    }

    @Test
    public void storeModuleEntryShouldStoreExpectedTtl() {
        // when
        target.storeModuleEntry("some-key",
                "some-value",
                ModuleCacheType.TEXT,
                12,
                "some-application",
                "some-module-code");

        // then
        final ModuleCacheRequest result = captureModuleCacheRequest();
        assertThat(result.getTtlseconds()).isEqualTo(12);
    }

    @Test
    public void storeModuleEntryShouldReturnFailedFutureIfKeyIsMissed() {
        // when
        final Future<Void> result = target.storeModuleEntry(null,
                "some-value",
                ModuleCacheType.TEXT,
                12,
                "some-application",
                "some-module-code");

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class);
        assertThat(result.cause().getMessage()).isEqualTo("Module cache 'key' can not be blank");
    }

    @Test
    public void storeModuleEntryShouldReturnFailedFutureIfValueIsMissed() {
        // when
        final Future<Void> result = target.storeModuleEntry("some-key",
                null,
                ModuleCacheType.TEXT,
                12,
                "some-application",
                "some-module-code");

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class);
        assertThat(result.cause().getMessage()).isEqualTo("Module cache 'value' can not be blank");
    }

    @Test
    public void storeModuleEntryShouldReturnFailedFutureIfTypeIsMissed() {
        // when
        final Future<Void> result = target.storeModuleEntry("some-key",
                "some-value",
                null,
                12,
                "some-application",
                "some-module-code");

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class);
        assertThat(result.cause().getMessage()).isEqualTo("Module cache 'type' can not be empty");
    }

    @Test
    public void storeModuleEntryShouldReturnFailedFutureIfModuleCodeIsMissed() {
        // when
        final Future<Void> result = target.storeModuleEntry("some-key",
                "some-value",
                ModuleCacheType.TEXT,
                12,
                "some-application",
                null);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class);
        assertThat(result.cause().getMessage()).isEqualTo("Module cache 'moduleCode' can not be blank");
    }

    @Test
    public void storeModuleEntryShouldCreateCallWithApiKeyInHeader() {
        // when
        target.storeModuleEntry("some-key",
                "some-value",
                ModuleCacheType.TEXT,
                12,
                "some-application",
                "some-module-code");

        // then
        final MultiMap result = captureStoreRequestHeaders();
        assertThat(result.get(HttpUtil.X_PBC_API_KEY_HEADER)).isEqualTo("pbc-api-key");
    }

    @Test
    public void retrieveModuleEntryShouldReturnFailedFutureIfModuleKeyIsMissed() {
        // when
        final Future<ModuleCacheResponse> result =
                target.retrieveModuleEntry(null, "some-module-code", "some-app");

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class);
        assertThat(result.cause().getMessage()).isEqualTo("Module cache 'key' can not be blank");
    }

    @Test
    public void retrieveModuleEntryShouldReturnFailedFutureIfModuleCodeIsMissed() {
        // when
        final Future<ModuleCacheResponse> result =
                target.retrieveModuleEntry("some-key", null, "some-app");

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class);
        assertThat(result.cause().getMessage()).isEqualTo("Module cache 'moduleCode' can not be blank");
    }

    @Test
    public void retrieveModuleEntryShouldCreateCallWithApiKeyInHeader() {
        // when
        target.retrieveModuleEntry("some-key", "some-module-code", "some-app");

        // then
        final MultiMap result = captureRetrieveRequestHeaders();
        assertThat(result.get(HttpUtil.X_PBC_API_KEY_HEADER)).isEqualTo("pbc-api-key");
    }

    @Test
    public void retrieveModuleEntryShouldCreateCallWithKeyInParams() {
        // when
        target.retrieveModuleEntry("some-key", "some-module-code", "some-app");

        // then
        final String result = captureRetrieveUrl();
        assertThat(result)
                .isEqualTo("http://cache-service/cache?key=module.some-module-code.some-key&application=some-app");
    }

    @Test
    public void retrieveModuleEntryShouldCreateCallWithBlankAppInParams() {
        // when
        target.retrieveModuleEntry("some-key", "some-module-code", null);

        // then
        final String result = captureRetrieveUrl();
        assertThat(result)
                .isEqualTo("http://cache-service/cache?key=module.some-module-code.some-key&application=");
    }

    @Test
    public void retrieveModuleEntryShouldReturnExpectedResponse() {
        // when
        final Future<ModuleCacheResponse> result =
                target.retrieveModuleEntry("some-key", "some-module-code", "some-app");

        // then
        assertThat(result.result())
                .isEqualTo(ModuleCacheResponse.of("some-key", ModuleCacheType.JSON, "some-value"));
    }

    @SneakyThrows
    private ModuleCacheRequest captureModuleCacheRequest() {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).post(anyString(), any(), captor.capture(), anyLong());
        return mapper.readValue(captor.getValue(), ModuleCacheRequest.class);
    }

    @SneakyThrows
    private MultiMap captureStoreRequestHeaders() {
        final ArgumentCaptor<MultiMap> captor = ArgumentCaptor.forClass(MultiMap.class);
        verify(httpClient).post(anyString(), captor.capture(), any(), anyLong());
        return captor.getValue();
    }

    @SneakyThrows
    private MultiMap captureRetrieveRequestHeaders() {
        final ArgumentCaptor<MultiMap> captor = ArgumentCaptor.forClass(MultiMap.class);
        verify(httpClient).get(anyString(), captor.capture(), anyLong());
        return captor.getValue();
    }

    @SneakyThrows
    private String captureRetrieveUrl() {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).get(captor.capture(), any(), anyLong());
        return captor.getValue();
    }
}
