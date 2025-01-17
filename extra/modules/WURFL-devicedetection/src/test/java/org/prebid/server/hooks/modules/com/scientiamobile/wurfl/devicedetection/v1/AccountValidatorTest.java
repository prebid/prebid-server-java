package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.settings.model.Account;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountValidatorTest {

    private AuctionContext auctionContext;

    @Mock
    private Account account;

    private AccountValidator validator;

    @BeforeEach
    void setUp() {
        auctionContext = AuctionContext.builder().account(account).build();
    }

    @Test
    void isAccountValidShouldReturnTrueWhenPublisherIdIsAllowed() {
        // given
        when(account.getId()).thenReturn("allowed-publisher");
        final var accountValidatorBuiler = AccountValidator.builder()
                .allowedPublisherIds(Collections.singletonMap("allowed-publisher", "allowed-publisher"))
                .auctionContext(auctionContext);
        assertThat(accountValidatorBuiler.toString()).isNotNull();
        validator = accountValidatorBuiler.build();

        // when
        final boolean result = validator.isAccountValid();

        // then
        assertThat(result).isTrue();
    }

    @Test
    void isAccountValidShouldReturnFalseWhenPublisherIdIsNotAllowed() {
        // given
        when(account.getId()).thenReturn("unknown-publisher");
        validator = AccountValidator.builder()
                .allowedPublisherIds(Collections.singletonMap("allowed-publisher", "allowed-publisher"))
                .auctionContext(auctionContext)
                .build();

        // when
        final boolean result = validator.isAccountValid();

        // then
        assertThat(result).isFalse();
    }

    @Test
    void isAccountValidShouldReturnFalseWhenAuctionContextIsNull() {
        // given
        validator = AccountValidator.builder()
                .allowedPublisherIds(Collections.singletonMap("allowed-publisher", "allowed-publisher"))
                .auctionContext(null)
                .build();

        // when
        final boolean result = validator.isAccountValid();

        // then
        assertThat(result).isFalse();
    }

    @Test
    void isAccountValidShouldReturnFalseWhenPublisherIdIsEmpty() {
        // given
        when(account.getId()).thenReturn("");
        validator = AccountValidator.builder()
                .allowedPublisherIds(Collections.singletonMap("allowed-publisher", "allowed-publisher"))
                .auctionContext(auctionContext)
                .build();

        // when
        final boolean result = validator.isAccountValid();

        // then
        assertThat(result).isFalse();
    }

    @Test
    void isAccountValidShouldReturnFalseWhenAccountIsNull() {
        // given
        when(auctionContext.getAccount()).thenReturn(null);
        validator = AccountValidator.builder()
                .allowedPublisherIds(Collections.singletonMap("allowed-publisher", "allowed-publisher"))
                .auctionContext(auctionContext)
                .build();

        // when
        final boolean result = validator.isAccountValid();

        // then
        assertThat(result).isFalse();
    }
}
