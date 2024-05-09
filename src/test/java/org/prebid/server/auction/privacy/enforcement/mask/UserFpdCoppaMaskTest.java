package org.prebid.server.auction.privacy.enforcement.mask;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.User;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;

public class UserFpdCoppaMaskTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private UserFpdActivityMask userFpdActivityMask;

    private UserFpdCoppaMask target;

    @Before
    public void setUp() {
        target = new UserFpdCoppaMask(userFpdActivityMask);
    }

    @Test
    public void maskUserShouldProperlyDelegateUser() {
        // given
        final User user = User.builder().build();

        // when
        target.maskUser(user);

        // then
        verify(userFpdActivityMask).maskUser(same(user), eq(true), eq(true));
    }

    @Test
    public void maskDeviceShouldProperlyDelegateDevice() {
        // given
        final Device device = Device.builder().build();

        // when
        target.maskDevice(device);

        // then
        verify(userFpdActivityMask).maskDevice(same(device), eq(true), eq(true));
    }
}
