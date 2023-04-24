package org.prebid.server.bidder.huaweiads.model.request;

public enum CreativeType {

    TEXT(1), BIG_PICTURE(2), BIG_PICTURE_2(3), GIF(4), VIDEO_TEXT(6), SMALL_PICTURE(7),
    THREE_SMALL_PICTURES_TEXT(8), VIDEO(9), ICON_TEXT(10), VIDEO_WITH_PICTURES_TEXT(11);

    private final Integer code;

    CreativeType(Integer code) {
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

}
