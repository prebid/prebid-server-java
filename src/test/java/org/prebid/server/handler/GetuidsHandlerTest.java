package org.prebid.server.handler;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
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
import org.prebid.server.cookie.model.UidWithExpiry;
import org.prebid.server.cookie.proto.Uids;

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

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private UidsCookieService uidsCookieService;

    private GetuidsHandler getuidsHandler;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpServerRequest;
    @Mock
    private HttpServerResponse httpServerResponse;

    @Before
    public void setUp() {
        given(routingContext.request()).willReturn(httpServerRequest);
        given(routingContext.response()).willReturn(httpServerResponse);

        getuidsHandler = new GetuidsHandler(uidsCookieService, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new GetuidsHandler(null, jacksonMapper));
    }

    @Test
    public void shouldReturnBuyerUidsJsonWithoutExpirationDate() {
        // given
        final Map<String, UidWithExpiry> uids = new HashMap<>();
        uids.put("rubicon", new UidWithExpiry("rubicon-uid",
                ZonedDateTime.parse("2018-12-30T12:30:40.123456789Z")));
        uids.put("adnxs", new UidWithExpiry("Appnexus-uid",
                ZonedDateTime.parse("2019-04-01T12:30:40.123456789Z")));

        given(uidsCookieService.parseFromRequest(any())).willReturn(new UidsCookie(
                Uids.builder().uids(uids).bday(ZonedDateTime.parse("2019-04-01T13:28:40.123456789Z")).build(),
                jacksonMapper));

        // when
        getuidsHandler.handle(routingContext);

        // then
        final String responseBody = getResponseBody();

        assertThat(responseBody).isNotBlank()
                .isEqualTo("{\"buyeruids\":{\"adnxs\":\"Appnexus-uid\",\"rubicon\":\"rubicon-uid\"}}");
    }

    @Test
    public void shouldReturnEmptyBuyerUids() {
        // given
        given(uidsCookieService.parseFromRequest(any())).willReturn(new UidsCookie(
                Uids.builder().uids(Collections.emptyMap()).build(),
                jacksonMapper));

        // when
        getuidsHandler.handle(routingContext);

        // then
        final String responseBody = getResponseBody();

        assertThat(responseBody).isNotBlank().isEqualTo("{}");
    }

    private String getResponseBody() {
        final ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpServerResponse).end(bodyCaptor.capture());
        return bodyCaptor.getValue();
    }
}
