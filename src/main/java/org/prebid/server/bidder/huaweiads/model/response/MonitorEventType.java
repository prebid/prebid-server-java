package org.prebid.server.bidder.huaweiads.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Getter
@AllArgsConstructor
public enum MonitorEventType {

    IMP("imp"),
    CLICK("click"),
    VAST_ERROR("vastError"),
    USER_CLOSE("skip&closeLinear"),
    PLAY_START("start"),
    PLAY_END("complete"),
    PLAY_RESUME("resume"),
    PLAY_PAUSE("pause"),
    SOUND_CLICK_OFF("mute"),
    SOUND_CLICK_ON("unmute"),
    WIN("win"),
    UNKNOWN("unknown");

    private final String event;

    public static MonitorEventType of(String monitorEvent) {
        return MonitorEventType.EVENT_TYPE_MAP.getOrDefault(monitorEvent, MonitorEventType.UNKNOWN);
    }

    private static final Map<String, MonitorEventType> EVENT_TYPE_MAP = stringToEventTypeMap();

    private static Map<String, MonitorEventType> stringToEventTypeMap() {
        final Map<String, MonitorEventType> result = new HashMap<>();
        result.put("imp", IMP);
        result.put("click", CLICK);
        result.put("vastError", VAST_ERROR);
        result.put("userClose", USER_CLOSE);
        result.put("playStart", PLAY_START);
        result.put("playEnd", PLAY_END);
        result.put("playResume", PLAY_RESUME);
        result.put("playPause", PLAY_PAUSE);
        result.put("soundClickOff", SOUND_CLICK_OFF);
        result.put("soundClickOn", SOUND_CLICK_ON);
        result.put("win", WIN);
        return Collections.unmodifiableMap(result);
    }
}

