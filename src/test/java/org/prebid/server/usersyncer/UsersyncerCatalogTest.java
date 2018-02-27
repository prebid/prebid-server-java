package org.prebid.server.usersyncer;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class UsersyncerCatalogTest {

    private static final String USERSYNCER = "rubicon";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private UsersyncerCatalog usersyncerCatalog;

    @Test
    public void isValidNameShouldReturnTrueForKnownBidders() {
        // given
        final Usersyncer usersyncer = mock(Usersyncer.class);
        given(usersyncer.name()).willReturn(USERSYNCER);

        usersyncerCatalog = new UsersyncerCatalog(singletonList(usersyncer));

        // when and then
        assertThat(usersyncerCatalog.isValidName(USERSYNCER)).isTrue();
    }

    @Test
    public void isValidNameShouldReturnFalseForUnknownBidders() {
        // given
        usersyncerCatalog = new UsersyncerCatalog(emptyList());

        // when and then
        assertThat(usersyncerCatalog.isValidName("unknown_bidder")).isFalse();
    }

    @Test
    public void byNameShouldReturnBidderNameForKnownBidder() {
        // given
        final Usersyncer usersyncer = mock(Usersyncer.class);
        given(usersyncer.name()).willReturn(USERSYNCER);

        usersyncerCatalog = new UsersyncerCatalog(singletonList(usersyncer));

        // when and then
        assertThat(usersyncerCatalog.byName(USERSYNCER)).isEqualTo(usersyncer);
    }

    @Test
    public void byNameShouldReturnNullForUnknownBidder() {
        // given
        usersyncerCatalog = new UsersyncerCatalog(emptyList());

        // when and then
        assertThat(usersyncerCatalog.byName("unknown_usersyncer")).isNull();
    }
}
