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
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.deals.model.TxnLog;
import org.prebid.server.vertx.LocalMessageCodec;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(VertxUnitRunner.class)
public class ApplicationEventServiceTest {

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

        final AuctionContext auctionContext = AuctionContext.builder().txnLog(TxnLog.create()).build();

        final Async async = testContext.async(2);
        final Handler<Object> consumeHandler = event -> {
            // then
            assertThat(event).isSameAs(auctionContext);
            assertThat(Vertx.currentContext()).isSameAs(initContext);
            async.countDown();
        };

        final ApplicationEventService applicationEventService = new ApplicationEventService(eventBus);
        final EventServiceInitializer eventServiceInitializer = new EventServiceInitializer(
                asList(new ProcessorStub(consumeHandler), new ProcessorStub(consumeHandler)), emptyList(), eventBus);

        // when
        final Async initAsync = testContext.async();
        initContext.runOnContext(ignored -> {
            eventServiceInitializer.initialize();
            initAsync.complete();
        });
        initAsync.await();

        publishContext.runOnContext(ignored -> applicationEventService.publishAuctionEvent(auctionContext));
    }

    @Test
    public void publishLineItemWinEventShouldPassEventToAllRecorders(TestContext testContext) {
        // given
        final Context initContext = vertx.getOrCreateContext();
        final Context publishContext = vertx.getOrCreateContext();

        final String lineItemId = "lineItemId1";

        final Async async = testContext.async(2);
        final Handler<Object> consumeHandler = event -> {
            // then
            assertThat(event).isSameAs(lineItemId);
            assertThat(Vertx.currentContext()).isSameAs(initContext);
            async.countDown();
        };

        final ApplicationEventService applicationEventService = new ApplicationEventService(eventBus);
        final EventServiceInitializer eventServiceInitializer = new EventServiceInitializer(
                asList(new ProcessorStub(consumeHandler), new ProcessorStub(consumeHandler)), emptyList(), eventBus);

        // when
        final Async initAsync = testContext.async();
        initContext.runOnContext(ignored -> {
            eventServiceInitializer.initialize();
            initAsync.complete();
        });
        initAsync.await();

        publishContext.runOnContext(ignored -> applicationEventService.publishLineItemWinEvent(lineItemId));
    }

    @Test
    public void publishDeliveryProgressUpdateEventShouldPassEventToAllRecorders(TestContext testContext) {
        // given
        final Context initContext = vertx.getOrCreateContext();
        final Context publishContext = vertx.getOrCreateContext();

        final Async async = testContext.async(2);
        final Handler<Object> consumeHandler = event -> {
            // then
            assertThat(event).isSameAs(null);
            assertThat(Vertx.currentContext()).isSameAs(initContext);
            async.countDown();
        };

        final ApplicationEventService applicationEventService = new ApplicationEventService(eventBus);
        final EventServiceInitializer eventServiceInitializer = new EventServiceInitializer(
                asList(new ProcessorStub(consumeHandler), new ProcessorStub(consumeHandler)), emptyList(), eventBus);

        // when
        final Async initAsync = testContext.async();
        initContext.runOnContext(ignored -> {
            eventServiceInitializer.initialize();
            initAsync.complete();
        });
        initAsync.await();

        publishContext.runOnContext(ignored -> applicationEventService.publishDeliveryUpdateEvent());
    }

    private static class ProcessorStub implements ApplicationEventProcessor {

        private final Handler<Object> handler;

        ProcessorStub(Handler<Object> handler) {
            this.handler = handler;
        }

        @Override
        public void processAuctionEvent(AuctionContext auctionContext) {
            handler.handle(auctionContext);
        }

        @Override
        public void processLineItemWinEvent(String lineItemId) {
            handler.handle(lineItemId);
        }

        @Override
        public void processDeliveryProgressUpdateEvent() {
            handler.handle(null);
        }
    }
}
