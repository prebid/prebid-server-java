package org.prebid.server.auction.privacy.enforcement.mask;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class UserFpdCoppaMaskTest extends VertxTest {

    @Mock
    private UserFpdActivityMask userFpdActivityMask;

    private UserFpdCoppaMask target;

    @BeforeEach
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
