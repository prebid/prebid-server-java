package org.prebid.server.handler.info;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.handler.info.filters.BaseOnlyBidderInfoFilterStrategy;
import org.prebid.server.handler.info.filters.EnabledOnlyBidderInfoFilterStrategy;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class BiddersHandlerTest extends VertxTest {

    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;

    private BiddersHandler target;

    @BeforeEach
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);
        given(httpResponse.setStatusCode(any(Integer.class))).willReturn(httpResponse);

        target = new BiddersHandler(
                bidderCatalog,
                List.of(
                        new EnabledOnlyBidderInfoFilterStrategy(bidderCatalog),
                        new BaseOnlyBidderInfoFilterStrategy(bidderCatalog)),
                jacksonMapper);
    }

    @Test
    public void creationShouldFailOnNullBidderCatalog() {
        assertThatNullPointerException()
                .isThrownBy(() -> new BiddersHandler(null, Collections.emptyList(), jacksonMapper));
    }

    @Test
    public void creationShouldFailOnNullStrategiesList() {
        assertThatNullPointerException().isThrownBy(() -> new BiddersHandler(bidderCatalog, null, jacksonMapper));
    }

    @Test
    public void creationShouldFailOnNullMapper() {
        assertThatNullPointerException()
                .isThrownBy(() -> new BiddersHandler(bidderCatalog, Collections.emptyList(), null));
    }

    @Test
    public void shouldRespondWithExpectedHeaders() {
        // given
        given(routingContext.queryParams()).willReturn(MultiMap.caseInsensitiveMultiMap());

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse)
                .putHeader(eq(new AsciiString("Content-Type")), eq(new AsciiString("application/json")));
    }

    @Test
    public void shouldReturnAllBiddersIfEnabledOnlyFlagIsNotPresent() {
        // given
        given(bidderCatalog.isActive("bidder3")).willReturn(true);
        given(bidderCatalog.names()).willReturn(new HashSet<>(asList("bidder2", "bidder3", "bidder1")));

        given(routingContext.queryParams()).willReturn(MultiMap.caseInsensitiveMultiMap());

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(HttpResponseStatus.OK.code());
        verify(httpResponse).end("[\"bidder1\",\"bidder2\",\"bidder3\"]");
    }

    @Test
    public void shouldReturnAllBiddersIfBaseOnlyFlagIsNotPresent() {
        // given
        given(bidderCatalog.isAlias("bidder3")).willReturn(true);
        given(bidderCatalog.names()).willReturn(new HashSet<>(asList("bidder2", "bidder3", "bidder1")));

        given(routingContext.queryParams()).willReturn(MultiMap.caseInsensitiveMultiMap());

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(HttpResponseStatus.OK.code());
        verify(httpResponse).end("[\"bidder1\",\"bidder2\",\"bidder3\"]");
    }

    @Test
    public void shouldRespondWithExpectedMessageAndStatusBadRequestWhenEnabledOnlyFlagHasInvalidValue() {
        // given
        given(routingContext.queryParams())
                .willReturn(MultiMap.caseInsensitiveMultiMap().add("enabledonly", "yes"));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(HttpResponseStatus.BAD_REQUEST.code());
        verify(httpResponse).end(eq("Invalid value for 'enabledonly' query param, must be of boolean type"));
    }

    @Test
    public void shouldRespondWithExpectedMessageAndStatusBadRequestWhenBaseOnlyFlagHasInvalidValue() {
        // given
        given(routingContext.queryParams())
                .willReturn(MultiMap.caseInsensitiveMultiMap().add("baseadaptersonly", "yes"));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(HttpResponseStatus.BAD_REQUEST.code());
        verify(httpResponse).end(eq("Invalid value for 'baseadaptersonly' query param, must be of boolean type"));
    }

    @Test
    public void shouldTolerateWithEnabledOnlyFlagInCaseInsensitiveMode() {
        // given
        given(routingContext.queryParams())
                .willReturn(MultiMap.caseInsensitiveMultiMap().add("enabledonly", "tRuE"));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(HttpResponseStatus.OK.code());
    }

    @Test
    public void shouldTolerateWithBaseOnlyFlagInCaseInsensitiveMode() {
        // given
        given(routingContext.queryParams())
                .willReturn(MultiMap.caseInsensitiveMultiMap().add("baseadaptersonly", "fAlSE"));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(HttpResponseStatus.OK.code());
    }

    @Test
    public void shouldRespondWithExpectedBodyAndStatusOkForEnabledOnlyFalseFlag() {
        // given
        given(routingContext.queryParams()).willReturn(MultiMap.caseInsensitiveMultiMap().add("enabledonly", "false"));
        given(bidderCatalog.names()).willReturn(new HashSet<>(asList("bidder2", "bidder3", "bidder1")));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(HttpResponseStatus.OK.code());
        verify(httpResponse).end(eq("[\"bidder1\",\"bidder2\",\"bidder3\"]"));
    }

    @Test
    public void shouldRespondWithExpectedBodyAndStatusOkForBaseOnlyFalseFlag() {
        // given
        given(routingContext.queryParams())
                .willReturn(MultiMap.caseInsensitiveMultiMap().add("baseadaptersonly", "false"));
        given(bidderCatalog.names()).willReturn(new HashSet<>(asList("bidder2", "bidder3", "bidder1")));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(HttpResponseStatus.OK.code());
        verify(httpResponse).end(eq("[\"bidder1\",\"bidder2\",\"bidder3\"]"));
    }

    @Test
    public void shouldRespondWithExpectedBodyAndStatusOkForEnabledOnlyTrueFlag() {
        // given
        given(routingContext.queryParams()).willReturn(MultiMap.caseInsensitiveMultiMap().add("enabledonly", "true"));
        given(bidderCatalog.isActive("bidder1")).willReturn(false);
        given(bidderCatalog.isActive("bidder2")).willReturn(false);
        given(bidderCatalog.isActive("bidder3")).willReturn(true);
        given(bidderCatalog.names()).willReturn(new HashSet<>(asList("bidder2", "bidder3", "bidder1")));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(HttpResponseStatus.OK.code());
        verify(httpResponse).end(eq("[\"bidder3\"]"));
    }

    @Test
    public void shouldRespondWithExpectedBodyAndStatusOkForBaseOnlyTrueFlag() {
        // given
        given(routingContext.queryParams())
                .willReturn(MultiMap.caseInsensitiveMultiMap().add("baseadaptersonly", "true"));
        given(bidderCatalog.isAlias("bidder1")).willReturn(false);
        given(bidderCatalog.isAlias("bidder2")).willReturn(false);
        given(bidderCatalog.isAlias("bidder3")).willReturn(true);
        given(bidderCatalog.names()).willReturn(new HashSet<>(asList("bidder2", "bidder3", "bidder1")));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(HttpResponseStatus.OK.code());
        verify(httpResponse).end(eq("[\"bidder1\",\"bidder2\"]"));
    }

    @Test
    public void shouldRespondWithExpectedBodyAndStatusOkForBaseOnlyTrueFlagAndEnabledOnlyTrueFlag() {
        // given
        given(routingContext.queryParams()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("enabledonly", "true")
                .add("baseadaptersonly", "true"));

        given(bidderCatalog.isAlias("bidder1")).willReturn(false);
        given(bidderCatalog.isAlias("bidder2")).willReturn(false);
        given(bidderCatalog.isAlias("bidder3")).willReturn(true);
        given(bidderCatalog.isAlias("bidder4")).willReturn(true);

        given(bidderCatalog.isActive("bidder1")).willReturn(true);
        given(bidderCatalog.isActive("bidder2")).willReturn(false);
        given(bidderCatalog.isActive("bidder3")).willReturn(true);
        given(bidderCatalog.isActive("bidder4")).willReturn(false);

        given(bidderCatalog.names()).willReturn(new HashSet<>(asList("bidder2", "bidder3", "bidder1", "bidder4")));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(HttpResponseStatus.OK.code());
        verify(httpResponse).end(eq("[\"bidder1\"]"));
    }
}
