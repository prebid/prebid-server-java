package org.prebid.server.handler;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.model.UidWithExpiry;
import org.prebid.server.model.Uids;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class GetuidsHandlerTest extends VertxTest {

    private static final String RUBICON = "rubicon";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private UidsCookieService uidsCookieService;

    private GetuidsHandler getuidsHandler;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;

    @Before
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);

        given(uidsCookieService.toCookie(any())).willCallRealMethod();

        getuidsHandler = new GetuidsHandler(uidsCookieService);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new GetuidsHandler(null));
    }

    @Test
    public void shouldReturnEncodedLegacyCookieAndDecodedJsonAsResponseBody() {
        // given
        final Map<String, UidWithExpiry> uids = new HashMap<>();
        uids.put(RUBICON, new UidWithExpiry("J5VLCWQP-26-CWFT", ZonedDateTime.parse("2017-12-30T12:30:40Z[GMT]")));

        given(uidsCookieService.parseFromRequest(any())).willReturn(new UidsCookie(Uids.builder()
                .uids(uids)
                .bday(ZonedDateTime.parse("2017-08-15T19:47:59.523908376Z"))
                .build()));
        given(routingContext.addCookie(any())).willReturn(routingContext);

        // when
        getuidsHandler.handle(routingContext);

        // then
        final Cookie cookie = captureCookie();
        String responseBody = captureResponseBody();

        assertThat(responseBody).isNotNull()
                .isEqualTo("{\"tempUIDs\":{\"rubicon\":{\"uid\":\"J5VLCWQP-26-CWFT\"," +
                        "\"expires\":\"2017-12-30T12:30:40.000000000Z\"}}," +
                        "\"bday\":\"2017-08-15T19:47:59.523908376Z\"}");
        assertThat(cookie.getName()).isNotNull().isEqualTo("uids");
        // this uids cookie stands for {"tempUIDs":{"rubicon":{"uid":"J5VLCWQP-26-CWFT",
        // "expires":"2017-12-30T12:30:40.000000000Z"}},"bday":"2017-08-15T19:47:59.523908376Z"}
        assertThat(cookie.getValue()).isNotNull().isEqualTo("eyJ0ZW1wVUlEcyI6eyJydWJpY29uIjp7InVpZCI6Iko1VkxDV1FQLTI2" +
                "LUNXRlQiLCJleHBpcmVzIjoiMjAxNy0xMi0zMFQxMjozMDo0MC4wMDAwMDAwMDBaIn19LCJiZGF5IjoiMjAxNy0wOC0xNVQxOTo0N" +
                "zo1OS41MjM5MDgzNzZaIn0=");
    }

    @Test
    public void shouldReturnCookieWithEmptyValueAndResponseBody() {
        // given
        given(uidsCookieService.parseFromRequest(any())).willReturn(new UidsCookie(Uids.builder()
                .uids(Collections.emptyMap())
                .bday(ZonedDateTime.parse("2017-08-15T19:47:59.523908376Z"))
                .build()));
        given(routingContext.addCookie(any())).willReturn(routingContext);

        // when
        getuidsHandler.handle(routingContext);

        // then
        final Cookie cookie = captureCookie();
        String responseBody = captureResponseBody();

        assertThat(responseBody).isNotNull().isEqualTo("{\"tempUIDs\":{},\"bday\":\"2017-08-15T19:47:59.523908376Z\"}");
        assertThat(cookie.getName()).isNotNull().isEqualTo("uids");
        // this uids cookie stands for {"tempUIDs":{},"bday":"2017-08-15T19:47:59.523908376Z"}
        assertThat(cookie.getValue()).isNotNull().isEqualTo("eyJ0ZW1wVUlEcyI6e30sImJkYXkiOiIyMDE3LTA4LTE1VDE5OjQ3O" +
                "jU5LjUyMzkwODM3NloifQ==");
    }

    private Cookie captureCookie() {
        final ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
        verify(routingContext).addCookie(cookieCaptor.capture());
        return cookieCaptor.getValue();
    }

    private String captureResponseBody() {
        final ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpResponse).end(bodyCaptor.capture());
        return bodyCaptor.getValue();
    }
}
