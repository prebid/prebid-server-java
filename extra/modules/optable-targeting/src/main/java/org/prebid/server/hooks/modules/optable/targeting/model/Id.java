package org.prebid.server.hooks.modules.optable.targeting.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import javax.validation.constraints.NotNull;

@AllArgsConstructor(staticName = "of")
@Builder(toBuilder = true)
@Value
public class Id {

    public static final String EMAIL = "e";

    public static final String PHONE = "p";

    public static final String ZIP = "z";

    public static final String DEVICE_IP_V_4 = "i4";

    public static final String DEVICE_IP_V_6 = "i6";

    public static final String APPLE_IDFA = "a";

    public static final String GOOGLE_GAID = "g";

    public static final String ROKU_RIDA = "r";

    public static final String SAMSUNG_TV_TIFA = "s";

    public static final String AMAZON_FIRE_AFAI = "f";

    public static final String NET_ID = "n";

    public static final String ID5 = "id5";

    public static final String UTIQ = "utiq";

    public static final String OPTABLE_VID = "v";

    /** Name of Identifier */
    @NotNull
    String name;

    /** Identifier's value */
    String value;
}
