package org.prebid.server.handler;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.cookie.model.UidWithExpiry;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.util.HttpUtil;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class GetuidsHandlerTest extends VertxTest {

    @Mock
    private UidsCookieService uidsCookieService;

    private GetuidsHandler getuidsHandler;
    @Mock(strictness = LENIENT)
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpServerRequest;
    @Mock(strictness = LENIENT)
    private HttpServerResponse httpServerResponse;

    @BeforeEach
    public void setUp() {
        given(routingContext.request()).willReturn(httpServerRequest);
        given(routingContext.response()).willReturn(httpServerResponse);

        given(httpServerResponse.putHeader(any(CharSequence.class), any(CharSequence.class)))
                .willReturn(httpServerResponse);

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

        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(new UidsCookie(Uids.builder().uids(uids).build(), jacksonMapper));

        // when
        getuidsHandler.handle(routingContext);

        // then
        verify(httpServerResponse).putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpUtil.APPLICATION_JSON_CONTENT_TYPE);

        final String responseBody = getResponseBody();

        assertThat(responseBody).isNotBlank()
                .isEqualTo("{\"buyeruids\":{\"adnxs\":\"Appnexus-uid\",\"rubicon\":\"rubicon-uid\"}}");
    }

    @Test
    public void shouldReturnEmptyBuyerUids() {
        // given
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class))).willReturn(new UidsCookie(
                Uids.builder().uids(Collections.emptyMap()).build(),
                jacksonMapper));

        // when
        getuidsHandler.handle(routingContext);

        // then
        verify(httpServerResponse).putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpUtil.APPLICATION_JSON_CONTENT_TYPE);

        final String responseBody = getResponseBody();

        assertThat(responseBody).isNotBlank().isEqualTo("{}");
    }

    private String getResponseBody() {
        final ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpServerResponse).end(bodyCaptor.capture());
        return bodyCaptor.getValue();
    }
}
