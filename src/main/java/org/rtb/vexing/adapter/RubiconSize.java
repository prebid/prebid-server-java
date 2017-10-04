package org.rtb.vexing.adapter;

import com.iab.openrtb.request.Format;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode
@AllArgsConstructor(staticName = "s", access = AccessLevel.PRIVATE)
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public final class RubiconSize {

    private static final Map<RubiconSize, Integer> SIZES = new HashMap<>();

    static {
        SIZES.put(s(468, 60), 1);
        SIZES.put(s(728, 90), 2);
        SIZES.put(s(728, 91), 2);
        SIZES.put(s(120, 600), 8);
        SIZES.put(s(160, 600), 9);
        SIZES.put(s(300, 600), 10);
        SIZES.put(s(300, 250), 15);
        SIZES.put(s(300, 251), 15);
        SIZES.put(s(336, 280), 16);
        SIZES.put(s(300, 100), 19);
        SIZES.put(s(980, 120), 31);
        SIZES.put(s(250, 360), 32);
        SIZES.put(s(180, 500), 33);
        SIZES.put(s(980, 150), 35);
        SIZES.put(s(468, 400), 37);
        SIZES.put(s(930, 180), 38);
        SIZES.put(s(320, 50), 43);
        SIZES.put(s(300, 50), 44);
        SIZES.put(s(300, 300), 48);
        SIZES.put(s(300, 1050), 54);
        SIZES.put(s(970, 90), 55);
        SIZES.put(s(970, 250), 57);
        SIZES.put(s(1000, 90), 58);
        SIZES.put(s(320, 80), 59);
        SIZES.put(s(1000, 1000), 61);
        SIZES.put(s(640, 480), 65);
        SIZES.put(s(320, 480), 67);
        SIZES.put(s(1800, 1000), 68);
        SIZES.put(s(320, 320), 72);
        SIZES.put(s(320, 160), 73);
        SIZES.put(s(980, 240), 78);
        SIZES.put(s(980, 300), 79);
        SIZES.put(s(980, 400), 80);
        SIZES.put(s(480, 300), 83);
        SIZES.put(s(970, 310), 94);
        SIZES.put(s(970, 210), 96);
        SIZES.put(s(480, 320), 101);
        SIZES.put(s(768, 1024), 102);
        SIZES.put(s(480, 280), 103);
        SIZES.put(s(1000, 300), 113);
        SIZES.put(s(320, 100), 117);
        SIZES.put(s(800, 250), 125);
        SIZES.put(s(200, 600), 126);
    }

    Integer w;
    Integer h;

    public static int toId(Format size) {
        return SIZES.getOrDefault(s(size.getW(), size.getH()), 0);
    }
}
