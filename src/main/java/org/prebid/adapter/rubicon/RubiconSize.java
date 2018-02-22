package org.prebid.adapter.rubicon;

import com.iab.openrtb.request.Format;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode
public final class RubiconSize {

    private static final Map<RubiconSize, Integer> SIZES = new HashMap<>();

    static {
        SIZES.put(size(468, 60), 1);
        SIZES.put(size(728, 90), 2);
        SIZES.put(size(728, 91), 2);
        SIZES.put(size(120, 600), 8);
        SIZES.put(size(160, 600), 9);
        SIZES.put(size(300, 600), 10);
        SIZES.put(size(300, 250), 15);
        SIZES.put(size(300, 251), 15);
        SIZES.put(size(336, 280), 16);
        SIZES.put(size(300, 100), 19);
        SIZES.put(size(980, 120), 31);
        SIZES.put(size(250, 360), 32);
        SIZES.put(size(180, 500), 33);
        SIZES.put(size(980, 150), 35);
        SIZES.put(size(468, 400), 37);
        SIZES.put(size(930, 180), 38);
        SIZES.put(size(320, 50), 43);
        SIZES.put(size(300, 50), 44);
        SIZES.put(size(300, 300), 48);
        SIZES.put(size(300, 1050), 54);
        SIZES.put(size(970, 90), 55);
        SIZES.put(size(970, 250), 57);
        SIZES.put(size(1000, 90), 58);
        SIZES.put(size(320, 80), 59);
        SIZES.put(size(1000, 1000), 61);
        SIZES.put(size(640, 480), 65);
        SIZES.put(size(320, 480), 67);
        SIZES.put(size(1800, 1000), 68);
        SIZES.put(size(320, 320), 72);
        SIZES.put(size(320, 160), 73);
        SIZES.put(size(980, 240), 78);
        SIZES.put(size(980, 300), 79);
        SIZES.put(size(980, 400), 80);
        SIZES.put(size(480, 300), 83);
        SIZES.put(size(970, 310), 94);
        SIZES.put(size(970, 210), 96);
        SIZES.put(size(480, 320), 101);
        SIZES.put(size(768, 1024), 102);
        SIZES.put(size(480, 280), 103);
        SIZES.put(size(1000, 300), 113);
        SIZES.put(size(320, 100), 117);
        SIZES.put(size(800, 250), 125);
        SIZES.put(size(200, 600), 126);
    }

    private final Integer w;
    private final Integer h;

    private RubiconSize(Integer w, Integer h) {
        this.w = w;
        this.h = h;
    }

    private static RubiconSize size(Integer w, Integer h) {
        return new RubiconSize(w, h);
    }

    public static int toId(Format size) {
        return SIZES.getOrDefault(size(size.getW(), size.getH()), 0);
    }
}
