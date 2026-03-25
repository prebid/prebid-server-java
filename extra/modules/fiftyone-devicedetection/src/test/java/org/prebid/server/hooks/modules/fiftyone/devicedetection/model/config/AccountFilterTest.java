package org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AccountFilterTest {

    private static final List<String> TEST_ALLOW_LIST = List.of("sister", "cousin");

    @Test
    public void shouldReturnAllowList() {
        // given
        final AccountFilter accountFilter = new AccountFilter();
        accountFilter.setAllowList(TEST_ALLOW_LIST);

        // when and then
        assertThat(accountFilter.getAllowList()).isEqualTo(TEST_ALLOW_LIST);
    }

    @Test
    public void shouldHaveDescription() {
        // given
        final AccountFilter accountFilter = new AccountFilter();
        accountFilter.setAllowList(TEST_ALLOW_LIST);

        // when and then
        assertThat(accountFilter.toString()).isNotBlank();
    }
}
