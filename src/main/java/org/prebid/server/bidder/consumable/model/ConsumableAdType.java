package org.prebid.server.bidder.consumable.model;

import com.iab.openrtb.request.Format;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ConsumableAdType {

    public static List<Integer> getSizeCodes(List<Format> formats) {
        if (CollectionUtils.isEmpty(formats)) {
            return Collections.emptyList();
        }
        return formats.stream()
                .map(format -> format.getW() + "x" + format.getH())
                .map(SIZE_MAP::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static final Map<String, Integer> SIZE_MAP = new HashMap<>();

    static {
        SIZE_MAP.put("120x90", 1);
        SIZE_MAP.put("468x60", 3);
        SIZE_MAP.put("728x90", 4);
        SIZE_MAP.put("300x250", 5);
        SIZE_MAP.put("160x600", 6);
        SIZE_MAP.put("120x600", 7);
        SIZE_MAP.put("300x100", 8);
        SIZE_MAP.put("180x150", 9);
        SIZE_MAP.put("336x280", 10);
        SIZE_MAP.put("240x400", 11);
        SIZE_MAP.put("234x60", 12);
        SIZE_MAP.put("88x31", 13);
        SIZE_MAP.put("120x60", 14);
        SIZE_MAP.put("120x240", 15);
        SIZE_MAP.put("125x125", 16);
        SIZE_MAP.put("220x250", 17);
        SIZE_MAP.put("250x90", 19);
        SIZE_MAP.put("0x0", 20);
        SIZE_MAP.put("200x90", 21);
        SIZE_MAP.put("300x50", 22);
        SIZE_MAP.put("320x50", 23);
        SIZE_MAP.put("320x480", 24);
        SIZE_MAP.put("185x185", 25);
        SIZE_MAP.put("620x45", 26);
        SIZE_MAP.put("300x125", 27);
        SIZE_MAP.put("800x250", 28);
        SIZE_MAP.put("970x90", 77);
        SIZE_MAP.put("970x250", 123);
        SIZE_MAP.put("300x600", 43);
        SIZE_MAP.put("970x66", 286);
        SIZE_MAP.put("970x280", 3230);
        SIZE_MAP.put("486x60", 429);
        SIZE_MAP.put("700x500", 374);
        SIZE_MAP.put("300x1050", 934);
        SIZE_MAP.put("320x100", 1578);
        SIZE_MAP.put("320x250", 331);
        SIZE_MAP.put("320x267", 3301);
        SIZE_MAP.put("728x250", 2730);
    }

    private ConsumableAdType() {
    }
}
