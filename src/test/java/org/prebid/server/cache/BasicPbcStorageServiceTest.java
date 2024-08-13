package org.prebid.server.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.cache.proto.request.module.ModuleCacheRequest;
import org.prebid.server.cache.proto.request.module.StorageDataType;
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
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class BasicPbcStorageServiceTest extends VertxTest {

    @Mock(strictness = LENIENT)
    private HttpClient httpClient;

    private BasicPbcStorageService target;

    @BeforeEach
    public void setUp() throws MalformedURLException, JsonProcessingException {
        target = new BasicPbcStorageService(
                httpClient,
                new URL("http://cache-service/cache"),
                "pbc-api-key",
                10,
                jacksonMapper);

        given(httpClient.post(anyString(), any(), any(), anyLong())).willReturn(Future.succeededFuture(
                HttpClientResponse.of(200, null, "someBody")));
        given(httpClient.get(anyString(), any(), anyLong())).willReturn(Future.succeededFuture(
                HttpClientResponse.of(200, null, mapper.writeValueAsString(
                        ModuleCacheResponse.of("some-key", StorageDataType.JSON, "some-value")))));
    }

    @Test
    public void storeModuleEntryShouldStoreExpectedKey() {
        // when
        target.storeEntry("some-key",
                "some-value",
                StorageDataType.TEXT,
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
        target.storeEntry("some-key",
                "some-value",
                StorageDataType.TEXT,
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
        target.storeEntry("some-key",
                "some-value",
                StorageDataType.TEXT,
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
        target.storeEntry("some-key",
                "some-value",
                StorageDataType.TEXT,
                12,
                "some-application",
                "some-module-code");

        // then
        final ModuleCacheRequest result = captureModuleCacheRequest();
        assertThat(result.getType()).isEqualTo(StorageDataType.TEXT);
    }

    @Test
    public void storeModuleEntryShouldStoreExpectedTtl() {
        // when
        target.storeEntry("some-key",
                "some-value",
                StorageDataType.TEXT,
                12,
                "some-application",
                "some-module-code");

        // then
        final ModuleCacheRequest result = captureModuleCacheRequest();
        assertThat(result.getTtlseconds()).isEqualTo(12);
    }

    @Test
    public void storeEntryShouldReturnFailedFutureIfKeyIsMissed() {
        // when
        final Future<Void> result = target.storeEntry(null,
                "some-value",
                StorageDataType.TEXT,
                12,
                "some-application",
                "some-module-code");

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class);
        assertThat(result.cause().getMessage()).isEqualTo("Module cache 'key' can not be blank");
    }

    @Test
    public void storeEntryShouldReturnFailedFutureIfValueIsMissed() {
        // when
        final Future<Void> result = target.storeEntry("some-key",
                null,
                StorageDataType.TEXT,
                12,
                "some-application",
                "some-module-code");

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class);
        assertThat(result.cause().getMessage()).isEqualTo("Module cache 'value' can not be blank");
    }

    @Test
    public void storeEntryShouldReturnFailedFutureIfApplicationIsMissed() {
        // when
        final Future<Void> result = target.storeEntry("some-key",
                "some-value",
                StorageDataType.TEXT,
                12,
                null,
                "some-module-code");

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class);
        assertThat(result.cause().getMessage()).isEqualTo("Module cache 'application' can not be blank");
    }

    @Test
    public void storeEntryShouldReturnFailedFutureIfTypeIsMissed() {
        // when
        final Future<Void> result = target.storeEntry("some-key",
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
    public void storeModuleEntryShouldReturnFailedFutureIfCodeIsMissed() {
        // when
        final Future<Void> result = target.storeEntry("some-key",
                "some-value",
                StorageDataType.TEXT,
                12,
                "some-application",
                null);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class);
        assertThat(result.cause().getMessage()).isEqualTo("Module cache 'moduleCode' can not be blank");
    }

    @Test
    public void storeEntryShouldCreateCallWithApiKeyInHeader() {
        // when
        target.storeEntry("some-key",
                "some-value",
                StorageDataType.TEXT,
                12,
                "some-application",
                "some-module-code");

        // then
        final MultiMap result = captureStoreRequestHeaders();
        assertThat(result.get(HttpUtil.X_PBC_API_KEY_HEADER)).isEqualTo("pbc-api-key");
    }

    @Test
    public void retrieveModuleEntryShouldReturnFailedFutureIfKeyIsMissed() {
        // when
        final Future<ModuleCacheResponse> result =
                target.retrieveEntry(null, "some-module-code", "some-app");

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class);
        assertThat(result.cause().getMessage()).isEqualTo("Module cache 'key' can not be blank");
    }

    @Test
    public void retrieveModuleEntryShouldReturnFailedFutureIfApplicationIsMissed() {
        // when
        final Future<ModuleCacheResponse> result =
                target.retrieveEntry("some-key", "some-module-code", null);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class);
        assertThat(result.cause().getMessage()).isEqualTo("Module cache 'application' can not be blank");
    }

    @Test
    public void retrieveModuleEntryShouldReturnFailedFutureIfCodeIsMissed() {
        // when
        final Future<ModuleCacheResponse> result =
                target.retrieveEntry("some-key", null, "some-app");

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class);
        assertThat(result.cause().getMessage()).isEqualTo("Module cache 'moduleCode' can not be blank");
    }

    @Test
    public void retrieveEntryShouldCreateCallWithApiKeyInHeader() {
        // when
        target.retrieveEntry("some-key", "some-module-code", "some-app");

        // then
        final MultiMap result = captureRetrieveRequestHeaders();
        assertThat(result.get(HttpUtil.X_PBC_API_KEY_HEADER)).isEqualTo("pbc-api-key");
    }

    @Test
    public void retrieveEntryShouldCreateCallWithKeyInParams() {
        // when
        target.retrieveEntry("some-key", "some-module-code", "some-app");

        // then
        final String result = captureRetrieveUrl();
        assertThat(result)
                .isEqualTo("http://cache-service/cache?k=module.some-module-code.some-key&a=some-app");
    }

    @Test
    public void retrieveEntryShouldReturnExpectedResponse() {
        // when
        final Future<ModuleCacheResponse> result =
                target.retrieveEntry("some-key", "some-module-code", "some-app");

        // then
        assertThat(result.result())
                .isEqualTo(ModuleCacheResponse.of("some-key", StorageDataType.JSON, "some-value"));
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
