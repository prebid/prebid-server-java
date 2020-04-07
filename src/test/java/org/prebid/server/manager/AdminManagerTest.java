package org.prebid.server.manager;

import io.vertx.core.logging.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class AdminManagerTest {

    private static final String KEY = "key";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private AdminManager adminManager;

    @Mock
    Logger logger;

    @Before
    public void setUp() {
        adminManager = new AdminManager();
    }

    @Test
    public void shouldExecuteFixedAmountOfTimes() {
        // given
        adminManager.setupByCounter(KEY, 20, (BiConsumer<Logger, String>) Logger::info,
                (BiConsumer<Logger, String>) (logger, text) -> logger.info(String.format("Done %s", text)));

        // when
        for (int i = 0; i < 30; i++) {
            adminManager.accept(KEY, logger, "Text");
        }

        // then
        verify(logger, times(20)).info("Text");
        verify(logger, times(10)).info("Done Text");
    }

    @Test
    public void shouldReturnTrueIfAdminManagerContainsKey() {
        // given
        adminManager.setupByCounter(KEY, 20, (BiConsumer<Logger, String>) Logger::info,
                (BiConsumer<Logger, String>) (logger, text) -> logger.info(String.format("Done %s", text)));

        // when
        boolean contains = adminManager.contains(KEY);

        // then
        assertThat(contains).isTrue();
    }

    @Test
    public void shouldReturnFalseIfKeyIsMissing() {
        // given
        adminManager.setupByCounter(KEY, 20, (BiConsumer<Logger, String>) Logger::info,
                (BiConsumer<Logger, String>) (logger, text) -> logger.info(String.format("Done %s", text)));

        // when
        boolean contains = adminManager.contains("WrongKey");

        // then
        assertThat(contains).isFalse();
    }

    @Test
    public void shouldExecuteByTime() throws InterruptedException {
        // given
        adminManager.setupByTime(KEY, 1000, (BiConsumer<Logger, String>) Logger::info,
                (BiConsumer<Logger, String>) (logger, text) -> logger.info(String.format("Done %s", text)));

        // when
        for (int i = 0; i < 15; i++) {
            adminManager.accept(KEY, logger, "Text");
            Thread.sleep(100);
        }

        // then
        verify(logger, times(10)).info("Text");
        verify(logger, times(5)).info("Done Text");
    }
}
