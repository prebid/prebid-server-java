package org.prebid.server.events;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.proto.openrtb.ext.response.Events;

import static org.assertj.core.api.Assertions.assertThat;

public class EventsServiceTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private EventsService eventsService;

    @Before
    public void setUp() {
        eventsService = new EventsService("http://external.org");
    }

    @Test
    public void createEventsShouldReturnExpectedEvent() {
        // when
        final Events events = eventsService.createEvent("bidId", "accountId");

        // then
        assertThat(events).isEqualTo(Events.of("http://external.org/event?t=win&b=bidId&a=accountId&f=i",
                "http://external.org/event?t=view&b=bidId&a=accountId&f=i"));
    }

    @Test
    public void winUrlTargetingShouldReturnExpectedUrl() {
        // when
        final String winUrlTargeting = eventsService.winUrlTargeting("accountId");

        // then
        assertThat(winUrlTargeting).isEqualTo("http://external.org/event?t=win&b=BIDID&a=accountId&f=i");
    }
}
