package org.prebid.server.bidder.huaweiads.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

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
        return Map.ofEntries(
                Map.entry("imp", IMP),
                Map.entry("click", CLICK),
                Map.entry("vastError", VAST_ERROR),
                Map.entry("userclose", USER_CLOSE),
                Map.entry("playStart", PLAY_START),
                Map.entry("playEnd", PLAY_END),
                Map.entry("playResume", PLAY_RESUME),
                Map.entry("playPause", PLAY_PAUSE),
                Map.entry("soundClickOff", SOUND_CLICK_OFF),
                Map.entry("soundClickOn", SOUND_CLICK_ON),
                Map.entry("win", WIN));
    }
}
