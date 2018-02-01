package org.rtb.vexing.settings;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.exception.InvalidRequestException;
import org.rtb.vexing.settings.model.StoredRequestResult;

import java.util.Collections;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        given(storedRequestFetcher.getStoredRequestsById(eq(singleton("id"))))
                .willReturn(Future.succeededFuture(StoredRequestResult.of(Collections.singletonMap("id", "json"),
                        emptyList())));
        // when
        final Future<StoredRequestResult> future = cachingStoredRequestFetcher.getStoredRequestsById(singleton("id"));
        cachingStoredRequestFetcher.getStoredRequestsById(singleton("id"));

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(StoredRequestResult.of(Collections.singletonMap("id", "json"),
                emptyList()));
        verify(storedRequestFetcher).getStoredRequestsById(eq(singleton("id")));
        verifyNoMoreInteractions(storedRequestFetcher);
    }

    @Test
    public void getStoredRequestByIdShouldPropagateFailure() {
        // given
        given(storedRequestFetcher.getStoredRequestsById(any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Not found")));

        // when
        Future<StoredRequestResult> future = cachingStoredRequestFetcher.getStoredRequestsById(singleton("id"));

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class)
                .hasMessage("Not found");
    }

    @Test
    public void getStoredRequestByIdShouldReturnResultWithErrorsOnNotSuccessiveCallToCacheAndErrorInDelegateCall() {
        // given
        given(storedRequestFetcher.getStoredRequestsById(eq(singleton("id"))))
                .willReturn(Future.succeededFuture(StoredRequestResult.of(emptyMap(),
                        singletonList("Stored requests for ids id was not found"))));

        // when
        Future<StoredRequestResult> future = cachingStoredRequestFetcher.getStoredRequestsById(singleton("id"));

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(StoredRequestResult.of(emptyMap(),
                singletonList("Stored requests for ids id was not found")));
    }
}
