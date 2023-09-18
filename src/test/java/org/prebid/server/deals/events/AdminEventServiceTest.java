package org.prebid.server.deals.events;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.deals.model.AdminCentralResponse;
import org.prebid.server.vertx.LocalMessageCodec;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(VertxUnitRunner.class)
public class AdminEventServiceTest {

    private Vertx vertx;
    private EventBus eventBus;

    @Before
    public void setUp(TestContext context) {
        vertx = Vertx.vertx();
        vertx.exceptionHandler(context.exceptionHandler());

        eventBus = vertx.eventBus();
        eventBus.registerCodec(LocalMessageCodec.create());
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void publishAuctionEventShouldPassEventToAllRecorders(TestContext testContext) {
        // given
        final Context initContext = vertx.getOrCreateContext();
        final Context publishContext = vertx.getOrCreateContext();

        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(null, null, null, null, null, null);

        final Handler<Object> consumeHandler = event -> {
            // then
            assertThat(event).isSameAs(adminCentralResponse);
            assertThat(Vertx.currentContext()).isSameAs(initContext);
        };

        final AdminEventService adminEventService = new AdminEventService(eventBus);
        final EventServiceInitializer eventServiceInitializer = new EventServiceInitializer(
                emptyList(), singletonList(new ProcessorStub(consumeHandler)), eventBus);

        // when
        final Async initAsync = testContext.async();
        initContext.runOnContext(ignored -> {
            eventServiceInitializer.initialize();
            initAsync.complete();
        });
        initAsync.await();

        publishContext.runOnContext(ignored -> adminEventService.publishAdminCentralEvent(adminCentralResponse));
    }

    private static class ProcessorStub implements AdminEventProcessor {

        private final Handler<Object> handler;

        ProcessorStub(Handler<Object> handler) {
            this.handler = handler;
        }

        @Override
        public void processAdminCentralEvent(AdminCentralResponse adminCentralResponse) {
            handler.handle(adminCentralResponse);
        }
    }
}
