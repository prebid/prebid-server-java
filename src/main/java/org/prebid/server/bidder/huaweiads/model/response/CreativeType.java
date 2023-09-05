package org.prebid.server.bidder.huaweiads.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

@Getter
@AllArgsConstructor
public enum CreativeType {

    TEXT(1),
    BIG_PICTURE(2),
    BIG_PICTURE_2(3),
    GIF(4),
    VIDEO_TEXT(6),
    SMALL_PICTURE(7),
    THREE_SMALL_PICTURES_TEXT(8),
    VIDEO(9),
    ICON_TEXT(10),
    VIDEO_WITH_PICTURES_TEXT(11),
    UNKNOWN(Integer.MIN_VALUE);

    private final int type;

    public static CreativeType of(Integer type) {
        return Arrays.stream(values())
                .filter(value -> Objects.equals(type, value.getType()))
                .findFirst()
                .orElse(UNKNOWN);
    }

}
