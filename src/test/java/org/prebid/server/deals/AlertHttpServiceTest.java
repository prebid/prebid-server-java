package org.prebid.server.deals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.deals.model.AlertEvent;
import org.prebid.server.deals.model.AlertPriority;
import org.prebid.server.deals.model.AlertProxyProperties;
import org.prebid.server.deals.model.AlertSource;
import org.prebid.server.deals.model.DeploymentProperties;
import org.prebid.server.vertx.http.HttpClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class AlertHttpServiceTest extends VertxTest {

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private HttpClient httpClient;

    private ZonedDateTime now;

    private Clock clock;

    private AlertHttpService alertHttpService;

    @Before
    public void setUp() {
        clock = Clock.fixed(Instant.parse("2019-07-26T10:00:00Z"), ZoneOffset.UTC);
        now = ZonedDateTime.now(clock);
        final HashMap<String, Long> servicesAlertPeriod = new HashMap<>();
        servicesAlertPeriod.put("pbs-error", 3L);
        alertHttpService = new AlertHttpService(jacksonMapper, httpClient, clock, DeploymentProperties.builder()
                .pbsVendor("pbsVendor")
                .pbsRegion("pbsRegion").pbsHostId("pbsHostId").subSystem("pbsSubSystem").system("pbsSystem")
                .dataCenter("pbsDataCenter").infra("pbsInfra").profile("pbsProfile").build(),
                AlertProxyProperties.builder().password("password").username("username").timeoutSec(5)
                        .url("http://localhost")
                        .alertTypes(servicesAlertPeriod).enabled(true).build());
    }

    @Test
    public void alertShouldNotSendAlertWhenServiceIsNotEnabled() {
        // given
        alertHttpService = new AlertHttpService(jacksonMapper, httpClient, clock, DeploymentProperties.builder()
                .pbsVendor("pbsVendor").pbsRegion("pbsRegion").pbsHostId("pbsHostId").subSystem("pbsSubSystem")
                .system("pbsSystem").dataCenter("pbsDataCenter").infra("pbsInfra").profile("pbsProfile").build(),
                AlertProxyProperties.builder().password("password").username("username").timeoutSec(5)
                        .alertTypes(emptyMap())
                        .url("http://localhost").enabled(false).build());

        // when
        alertHttpService.alert("pbs", AlertPriority.HIGH, "errorMessage");

        // then
        verifyNoInteractions(httpClient);
    }

    @Test
    public void alertShouldSendAlertWhenServiceIsEnabled() throws JsonProcessingException {
        // given
        given(httpClient.post(anyString(), any(), anyString(), anyLong()))
                .willReturn(Future.succeededFuture());

        // when
        alertHttpService.alert("pbs", AlertPriority.HIGH, "errorMessage");

        // then
        final List<AlertEvent> requestPayloadObject = getRequestPayload();
        final String id = requestPayloadObject.get(0).getId();
        assertThat(requestPayloadObject)
                .isEqualTo(singletonList(AlertEvent.builder().id(id).action("RAISE").priority(AlertPriority.HIGH)
                        .updatedAt(now).name("pbs").details("errorMessage")
                        .source(AlertSource.builder().env("pbsProfile").dataCenter("pbsDataCenter").region("pbsRegion")
                                .system("pbsSystem").subSystem("pbsSubSystem").hostId("pbsHostId").build()).build()));
    }

    @Test
    public void alertWithPeriodShouldSendAlertFirstTimeWithPassedAlertPriority() throws JsonProcessingException {
        // given
        given(httpClient.post(anyString(), any(), anyString(), anyLong()))
                .willReturn(Future.succeededFuture());

        // when
        alertHttpService.alertWithPeriod("pbs", "pbs-error", AlertPriority.MEDIUM, "errorMessage");

        // then
        final List<AlertEvent> requestPayloadObject = getRequestPayload();
        final String id = requestPayloadObject.get(0).getId();
        assertThat(requestPayloadObject)
                .isEqualTo(singletonList(AlertEvent.builder().id(id).action("RAISE").priority(AlertPriority.MEDIUM)
                        .updatedAt(now).name("pbs-error").details("Service pbs failed to send request 1 time(s) "
                                + "with error message : errorMessage")
                        .source(AlertSource.builder().env("pbsProfile").dataCenter("pbsDataCenter").region("pbsRegion")
                                .system("pbsSystem").subSystem("pbsSubSystem").hostId("pbsHostId").build()).build()));
    }

    @Test
    public void alertWithPeriodShouldSendAlertWithPassedPriorityAndWithHighPriorityAfterPeriodLimitReached()
            throws JsonProcessingException {
        // given
        given(httpClient.post(anyString(), any(), anyString(), anyLong()))
                .willReturn(Future.succeededFuture());

        // when
        IntStream.range(0, 3).forEach(ignored ->
                alertHttpService.alertWithPeriod("pbs", "pbs-error", AlertPriority.MEDIUM, "errorMessage"));

        // then
        final ArgumentCaptor<String> requestBodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClient, times(2)).post(anyString(), any(), requestBodyCaptor.capture(), anyLong());

        final List<String> requests = requestBodyCaptor.getAllValues();
        final List<AlertEvent> alertEvents1 = parseAlertEvents(requests.get(0));

        final String id = alertEvents1.get(0).getId();

        assertThat(alertEvents1)
                .isEqualTo(singletonList(AlertEvent.builder().id(id).action("RAISE").priority(AlertPriority.MEDIUM)
                        .updatedAt(now).name("pbs-error").details("Service pbs failed to send request 1 time(s) "
                                + "with error message : errorMessage")
                        .source(AlertSource.builder().env("pbsProfile").dataCenter("pbsDataCenter").region("pbsRegion")
                                .system("pbsSystem").subSystem("pbsSubSystem").hostId("pbsHostId").build()).build()));

        final List<AlertEvent> alertEvents2 = parseAlertEvents(requests.get(1));
        final String id2 = alertEvents2.get(0).getId();

        assertThat(alertEvents2)
                .isEqualTo(singletonList(AlertEvent.builder().id(id2).action("RAISE").priority(AlertPriority.HIGH)
                        .updatedAt(now).name("pbs-error").details("Service pbs failed to send request 3 time(s) "
                                + "with error message : errorMessage")
                        .source(AlertSource.builder().env("pbsProfile").dataCenter("pbsDataCenter").region("pbsRegion")
                                .system("pbsSystem").subSystem("pbsSubSystem").hostId("pbsHostId").build()).build()));
    }

    @Test
    public void alertWithPeriodShouldSendAlertTwoTimesForUnknownServiceWithDefaultPeriod()
            throws JsonProcessingException {
        // given
        given(httpClient.post(anyString(), any(), anyString(), anyLong()))
                .willReturn(Future.succeededFuture());

        // when
        IntStream.range(0, 15).forEach(ignored ->
                alertHttpService.alertWithPeriod("unknown", "unknown-error", AlertPriority.MEDIUM, "errorMessage"));

        // then
        final ArgumentCaptor<String> requestBodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClient, times(2)).post(anyString(), any(), requestBodyCaptor.capture(), anyLong());

        final List<String> requests = requestBodyCaptor.getAllValues();
        final List<AlertEvent> alertEvents1 = parseAlertEvents(requests.get(0));

        final String id = alertEvents1.get(0).getId();

        assertThat(alertEvents1)
                .isEqualTo(singletonList(AlertEvent.builder().id(id).action("RAISE").priority(AlertPriority.MEDIUM)
                        .updatedAt(now).name("unknown-error").details("Service unknown failed to send request"
                                + " 1 time(s) with error message : errorMessage")
                        .source(AlertSource.builder().env("pbsProfile").dataCenter("pbsDataCenter").region("pbsRegion")
                                .system("pbsSystem").subSystem("pbsSubSystem").hostId("pbsHostId").build()).build()));

        final List<AlertEvent> alertEvents2 = parseAlertEvents(requests.get(1));
        final String id2 = alertEvents2.get(0).getId();

        assertThat(alertEvents2)
                .isEqualTo(singletonList(AlertEvent.builder().id(id2).action("RAISE").priority(AlertPriority.HIGH)
                        .updatedAt(now).name("unknown-error").details("Service unknown failed to send request"
                                + " 15 time(s) with error message : errorMessage")
                        .source(AlertSource.builder().env("pbsProfile").dataCenter("pbsDataCenter").region("pbsRegion")
                                .system("pbsSystem").subSystem("pbsSubSystem").hostId("pbsHostId").build()).build()));
    }

    @Test
    public void alertWithPeriodShouldSendAlertFirstTimeWithPassedAlertPriorityForUnknownService()
            throws JsonProcessingException {
        // given
        given(httpClient.post(anyString(), any(), anyString(), anyLong()))
                .willReturn(Future.succeededFuture());

        // when
        alertHttpService.alertWithPeriod("unknown", "unknown-error", AlertPriority.MEDIUM, "errorMessage");

        // then
        final List<AlertEvent> requestPayloadObject = getRequestPayload();
        final String id = requestPayloadObject.get(0).getId();
        assertThat(requestPayloadObject)
                .isEqualTo(singletonList(AlertEvent.builder().id(id).action("RAISE").priority(AlertPriority.MEDIUM)
                        .updatedAt(now).name("unknown-error").details("Service unknown failed to send request "
                                + "1 time(s) with error message : errorMessage")
                        .source(AlertSource.builder().env("pbsProfile").dataCenter("pbsDataCenter").region("pbsRegion")
                                .system("pbsSystem").subSystem("pbsSubSystem").hostId("pbsHostId").build()).build()));
    }

    private List<AlertEvent> getRequestPayload() throws JsonProcessingException {
        final ArgumentCaptor<String> requestBodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).post(anyString(), any(), requestBodyCaptor.capture(), anyLong());
        return parseAlertEvents(requestBodyCaptor.getValue());
    }

    private List<AlertEvent> parseAlertEvents(String row) throws JsonProcessingException {
        return mapper.readValue(row,
                new TypeReference<>() {
                });
    }
}
