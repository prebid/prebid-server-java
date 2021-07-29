package org.prebid.server.settings.helper;

import org.junit.Test;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.settings.model.StoredDataType;
import org.prebid.server.settings.model.StoredItem;

import java.util.HashSet;
import java.util.Set;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class StoredItemResolverTest {

    @Test
    public void resolveShouldFailWhenNoStoredData() {
        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> StoredItemResolver.resolve(StoredDataType.imp, null, "id", emptySet()))
                .withMessage("No stored imp found for id: id");
    }

    @Test
    public void resolveShouldFailWhenMultipleStoredDataButNoAccountInRequest() {
        // given
        final Set<StoredItem> storedItems = givenMultipleStoredData();

        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> StoredItemResolver.resolve(StoredDataType.imp, null, "id", storedItems))
                .withMessage("Multiple stored imps found for id: id but no account was specified");
    }

    @Test
    public void resolveShouldFailWhenMultipleStoredDataButAccountDiffers() {
        // given
        final Set<StoredItem> storedItems = givenMultipleStoredData();

        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> StoredItemResolver.resolve(StoredDataType.imp, "1003", "id", storedItems))
                .withMessage("No stored imp found among multiple id: id for account: 1003");
    }

    @Test
    public void resolveShouldReturnResultWhenMultipleStoredDataForAppropriateAccount() {
        // given
        final Set<StoredItem> storedItems = givenMultipleStoredData();

        // when
        final StoredItem storedItem = StoredItemResolver.resolve(StoredDataType.imp, "1002", "id", storedItems);

        // then
        assertThat(storedItem).isEqualTo(StoredItem.of("1002", "data2"));
    }

    @Test
    public void resolveShouldReturnResultWhenSingleStoredDataButNoAccountInRequest() {
        // given
        final Set<StoredItem> storedItems = new HashSet<>();
        storedItems.add(StoredItem.of("1001", "data1"));

        // when
        final StoredItem storedItem = StoredItemResolver.resolve(StoredDataType.imp, "1001", "", storedItems);

        // then
        assertThat(storedItem).isEqualTo(StoredItem.of("1001", "data1"));
    }

    @Test
    public void resolveShouldReturnResultWhenSingleStoredDataButNoAccountInStoredData() {
        // given
        final Set<StoredItem> storedItems = new HashSet<>();
        storedItems.add(StoredItem.of(null, "data1"));

        // when
        final StoredItem storedItem = StoredItemResolver.resolve(StoredDataType.imp, "1001", "id", storedItems);

        // then
        assertThat(storedItem).isEqualTo(StoredItem.of(null, "data1"));
    }

    @Test
    public void resolveShouldReturnResultWhenSingleStoredDataButNoAccountBothInRequestAndStoredData() {
        // given
        final Set<StoredItem> storedItems = new HashSet<>();
        storedItems.add(StoredItem.of(null, "data1"));

        // when
        final StoredItem storedItem = StoredItemResolver.resolve(StoredDataType.imp, null, "id", storedItems);

        // then
        assertThat(storedItem).isEqualTo(StoredItem.of(null, "data1"));
    }

    @Test
    public void resolveShouldFailWhenSingleStoredDataForAppropriateAccount() {
        // given
        final Set<StoredItem> storedItems = givenSingleStoredData();

        // when
        final StoredItem storedItem = StoredItemResolver.resolve(StoredDataType.imp, "1001", "id", storedItems);

        // then
        assertThat(storedItem).isEqualTo(StoredItem.of("1001", "data1"));
    }

    @Test
    public void resolveShouldFailWhenSingleStoredDataButAccountDiffers() {
        // given
        final Set<StoredItem> storedItems = givenSingleStoredData();

        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> StoredItemResolver.resolve(StoredDataType.imp, "1002", "id", storedItems))
                .withMessage("No stored imp found for id: id for account: 1002");
    }

    private static Set<StoredItem> givenSingleStoredData() {
        final Set<StoredItem> storedItems = new HashSet<>();
        storedItems.add(StoredItem.of("1001", "data1"));
        return storedItems;
    }

    private static Set<StoredItem> givenMultipleStoredData() {
        final Set<StoredItem> storedItems = new HashSet<>();
        storedItems.add(StoredItem.of("1001", "data1"));
        storedItems.add(StoredItem.of("1002", "data2"));
        return storedItems;
    }
}
