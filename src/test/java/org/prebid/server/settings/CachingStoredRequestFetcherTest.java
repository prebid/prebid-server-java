package org.prebid.server.settings;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.GlobalTimeout;
import org.prebid.server.settings.model.StoredRequestResult;

import java.util.Collections;

import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class CachingStoredRequestFetcherTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private StoredRequestFetcher storedRequestFetcher;

    private CachingStoredRequestFetcher cachingStoredRequestFetcher;

    @Before
    public void setUp() {
        cachingStoredRequestFetcher = new CachingStoredRequestFetcher(storedRequestFetcher, 360, 100);
    }

    @Test
    public void creationShouldFailOnInvalidArguments() {
        assertThatNullPointerException().isThrownBy(() -> new CachingStoredRequestFetcher(null, 1, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new CachingStoredRequestFetcher(
                storedRequestFetcher, 0, 1)).withMessage("ttl and size must be positive");
        assertThatIllegalArgumentException().isThrownBy(() -> new CachingStoredRequestFetcher(
                storedRequestFetcher, 1, 0)).withMessage("ttl and size must be positive");
    }

    @Test
    public void getStoredRequestByIdShouldReturnResultOnSuccessiveCalls() {
        // given
        final GlobalTimeout timeout = GlobalTimeout.create(500);
        given(storedRequestFetcher.getStoredRequestsById(eq(singleton("id")), same(timeout)))
                .willReturn(Future.succeededFuture(StoredRequestResult.of(Collections.singletonMap("id", "json"),
                        emptyList())));
        // when
        final Future<StoredRequestResult> future =
                cachingStoredRequestFetcher.getStoredRequestsById(singleton("id"), timeout);
        cachingStoredRequestFetcher.getStoredRequestsById(singleton("id"), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(StoredRequestResult.of(Collections.singletonMap("id", "json"),
                emptyList()));
        verify(storedRequestFetcher).getStoredRequestsById(eq(singleton("id")), same(timeout));
        verifyNoMoreInteractions(storedRequestFetcher);
    }

    @Test
    public void getStoredRequestByIdShouldPropagateFailure() {
        // given
        given(storedRequestFetcher.getStoredRequestsById(any(), any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Not found")));

        // when
        final Future<StoredRequestResult> future =
                cachingStoredRequestFetcher.getStoredRequestsById(singleton("id"), GlobalTimeout.create(500));

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class)
                .hasMessage("Not found");
    }

    @Test
    public void getStoredRequestByIdShouldReturnResultWithErrorsOnNotSuccessiveCallToCacheAndErrorInDelegateCall() {
        // given
        given(storedRequestFetcher.getStoredRequestsById(eq(singleton("id")), any()))
                .willReturn(Future.succeededFuture(StoredRequestResult.of(emptyMap(),
                        singletonList("Stored requests for ids id was not found"))));

        // when
        final Future<StoredRequestResult> future =
                cachingStoredRequestFetcher.getStoredRequestsById(singleton("id"), GlobalTimeout.create(500));

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(StoredRequestResult.of(emptyMap(),
                singletonList("Stored requests for ids id was not found")));
    }
}
