package org.prebid.server.bidder.huaweiads.model.response;

public enum InteractionType {

    APP_PROMOTION(3);

    private final Integer code;

    InteractionType(Integer code) {
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
