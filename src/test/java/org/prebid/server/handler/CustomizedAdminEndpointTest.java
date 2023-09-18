package org.prebid.server.handler;

import io.vertx.core.Handler;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthHandler;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class CustomizedAdminEndpointTest extends VertxTest {

    private static final String PATH = "test";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private CustomizedAdminEndpoint target;

    @Mock
    private Handler<RoutingContext> handler;

    @Mock
    private Router router;

    @Mock
    private Route route;

    private final Map<String, String> adminEndpointCredentials = Collections.singletonMap("user", "pass");

    @Before
    public void setUp() {
        target = new CustomizedAdminEndpoint(PATH, handler, true, true);

        given(router.route(any())).willReturn(route);
        given(route.handler(any())).willReturn(route);
    }

    @Test
    public void routeShouldThrowExceptionWhenProtectedButCredentialsNotProvided() {
        // when and then
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> target.router(router))
                .withMessage("Credentials for admin endpoint is empty.");
    }

    @Test
    public void routeShouldCallAuthAndHandlerWhenProtectedAndCredentialsProvided() {
        // given
        target.withCredentials(adminEndpointCredentials);

        // when
        target.router(router);

        // then
        verify(router).route(PATH);
        verify(route).handler(any(AuthHandler.class));
        verify(route).handler(handler);

        verifyNoMoreInteractions(route);
        verifyNoMoreInteractions(router);
    }

    @Test
    public void routeShouldCallRouterWhenProtectedAndNoCredentials() {
        // given
        target.withCredentials(Collections.emptyMap());

        // when
        target.router(router);

        // then
        verify(router).route(PATH);
        verify(route).handler(any(AuthHandler.class));
        verify(route).handler(handler);

        verifyNoMoreInteractions(route);
        verifyNoMoreInteractions(router);
    }

    @Test
    public void routeShouldCallRouterWhenNotProtectedAndCredentialsProvided() {
        // given
        target = new CustomizedAdminEndpoint(PATH, handler, true, false);
        target.withCredentials(adminEndpointCredentials);

        // when
        target.router(router);

        // then
        verify(router).route(PATH);
        verify(route).handler(handler);

        verifyNoMoreInteractions(route);
        verifyNoMoreInteractions(router);
    }

    @Test
    public void isOnApplicationPortShouldReturnTrue() {
        // when and then
        assertThat(target.isOnApplicationPort()).isTrue();
    }
}
