package org.rtb.vexing.adapter;

import org.junit.Test;
import org.rtb.vexing.adapter.model.ExchangeCall;
import org.rtb.vexing.adapter.model.HttpRequest;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.PreBidRequestContext;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.model.response.UsersyncInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class AdapterCatalogTest {

    @Test
    public void constructorShouldFailONullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new AdapterCatalog(null));
    }

    @Test
    public void getByCodeShouldReturnAdapterWhenAdapterExist() {
        // given
        final List<Adapter> adapters = Arrays.asList(createAdapter());

        final AdapterCatalog catalog = new AdapterCatalog(adapters);

        // when
        final Adapter result = catalog.getByCode("test");

        //then
        assertThat(result.code()).isEqualTo("test");
    }

    @Test
    public void getByCodeShouldReturnNullWhenAdapterNotExist() {
        // given
        final List<Adapter> adapters = new ArrayList<>();
        final AdapterCatalog catalog = new AdapterCatalog(adapters);

        // when
        final Adapter result = catalog.getByCode("test");

        //then
        assertThat(result).isNull();
    }

    @Test
    public void isValidCodeShouldReturnFalseWhenAdapterNotExist() {
        // given
        final List<Adapter> adapters = new ArrayList<>();
        final AdapterCatalog catalog = new AdapterCatalog(adapters);

        // when
        final Boolean result = catalog.isValidCode("test");

        //then
        assertThat(result).isFalse();
    }

    @Test
    public void isValidCodeShouldReturnTrueWhenAdapterExist() {
        // given
        final List<Adapter> adapters = Arrays.asList(createAdapter());
        final AdapterCatalog catalog = new AdapterCatalog(adapters);

        // when
        final Boolean result = catalog.isValidCode("test");

        //then
        assertThat(result).isTrue();
    }

    private static Adapter createAdapter() {
        return new Adapter() {

            @Override
            public String code() {
                return "test";
            }

            @Override
            public String cookieFamily() {
                return null;
            }

            @Override
            public UsersyncInfo usersyncInfo() {
                return null;
            }

            @Override
            public List<HttpRequest> makeHttpRequests(Bidder bidder, PreBidRequestContext preBidRequestContext)
                    throws PreBidException {
                return null;
            }

            @Override
            public List<Bid.BidBuilder> extractBids(Bidder bidder, ExchangeCall exchangeCall) throws PreBidException {
                return null;
            }

            @Override
            public boolean tolerateErrors() {
                return false;
            }
        };
    }
}
