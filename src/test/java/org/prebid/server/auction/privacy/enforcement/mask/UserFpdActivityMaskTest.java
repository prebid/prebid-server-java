package org.prebid.server.auction.privacy.enforcement.mask;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.User;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static java.util.Collections.emptySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;

public class UserFpdActivityMaskTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private UserFpdTcfMask userFpdTcfMask;

    private UserFpdActivityMask target;

    @Before
    public void setUp() {
        target = new UserFpdActivityMask(userFpdTcfMask);
    }

    @Test
    public void maskUserShouldProperlyDelegateUfpdParameter() {
        // given
        final User user = User.builder().build();

        // when
        target.maskUser(user, true, false);

        // then
        verify(userFpdTcfMask).maskUser(same(user), eq(true), eq(false), eq(emptySet()));
    }

    @Test
    public void maskUserShouldProperlyDelegateEidsParameter() {
        // given
        final User user = User.builder().build();

        // when
        target.maskUser(user, false, true);

        // then
        verify(userFpdTcfMask).maskUser(same(user), eq(false), eq(true), eq(emptySet()));
    }

    @Test
    public void maskDeviceShouldProperlyDelegateUfpdParameter() {
        // given
        final Device device = Device.builder().build();

        // when
        target.maskDevice(device, true, false);

        // then
        verify(userFpdTcfMask).maskDevice(same(device), eq(false), eq(false), eq(true));
    }

    @Test
    public void maskDeviceShouldProperlyDelegateGeoParameter() {
        // given
        final Device device = Device.builder().build();

        // when
        target.maskDevice(device, false, true);

        // then
        verify(userFpdTcfMask).maskDevice(same(device), eq(true), eq(true), eq(false));
    }
}
