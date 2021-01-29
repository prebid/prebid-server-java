package org.prebid.server.bidder.rubicon;

import com.iab.openrtb.request.Format;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@EqualsAndHashCode
final class RubiconSize {

    private static final Map<RubiconSize, Integer> SIZES = new HashMap<>();
    private static final Comparator<Integer> COMPARATOR = new MASComparator().thenComparing(Comparator.naturalOrder());

    static {
        SIZES.put(size(468, 60), 1);
        SIZES.put(size(728, 90), 2);
        SIZES.put(size(728, 91), 2);
        SIZES.put(size(120, 90), 5);
        SIZES.put(size(125, 125), 7);
        SIZES.put(size(120, 600), 8);
        SIZES.put(size(160, 600), 9);
        SIZES.put(size(300, 600), 10);
        SIZES.put(size(200, 200), 13);
        SIZES.put(size(250, 250), 14);
        SIZES.put(size(300, 250), 15);
        SIZES.put(size(300, 251), 15);
        SIZES.put(size(336, 280), 16);
        SIZES.put(size(240, 400), 17);
        SIZES.put(size(300, 100), 19);
        SIZES.put(size(980, 120), 31);
        SIZES.put(size(250, 360), 32);
        SIZES.put(size(180, 500), 33);
        SIZES.put(size(980, 150), 35);
        SIZES.put(size(468, 400), 37);
        SIZES.put(size(930, 180), 38);
        SIZES.put(size(750, 100), 39);
        SIZES.put(size(750, 200), 40);
        SIZES.put(size(750, 300), 41);
        SIZES.put(size(320, 50), 43);
        SIZES.put(size(300, 50), 44);
        SIZES.put(size(300, 300), 48);
        SIZES.put(size(1024, 768), 53);
        SIZES.put(size(300, 1050), 54);
        SIZES.put(size(970, 90), 55);
        SIZES.put(size(970, 250), 57);
        SIZES.put(size(1000, 90), 58);
        SIZES.put(size(320, 80), 59);
        SIZES.put(size(320, 150), 60);
        SIZES.put(size(1000, 1000), 61);
        SIZES.put(size(580, 500), 64);
        SIZES.put(size(640, 480), 65);
        SIZES.put(size(930, 600), 66);
        SIZES.put(size(320, 480), 67);
        SIZES.put(size(1800, 1000), 68);
        SIZES.put(size(320, 320), 72);
        SIZES.put(size(320, 160), 73);
        SIZES.put(size(980, 240), 78);
        SIZES.put(size(980, 300), 79);
        SIZES.put(size(980, 400), 80);
        SIZES.put(size(480, 300), 83);
        SIZES.put(size(300, 120), 85);
        SIZES.put(size(548, 150), 90);
        SIZES.put(size(970, 310), 94);
        SIZES.put(size(970, 100), 95);
        SIZES.put(size(970, 210), 96);
        SIZES.put(size(480, 320), 101);
        SIZES.put(size(768, 1024), 102);
        SIZES.put(size(480, 280), 103);
        SIZES.put(size(250, 800), 105);
        SIZES.put(size(320, 240), 108);
        SIZES.put(size(1000, 300), 113);
        SIZES.put(size(320, 100), 117);
        SIZES.put(size(800, 250), 125);
        SIZES.put(size(200, 600), 126);
        SIZES.put(size(980, 600), 144);
        SIZES.put(size(980, 150), 145);
        SIZES.put(size(1000, 250), 152);
        SIZES.put(size(640, 320), 156);
        SIZES.put(size(320, 250), 159);
        SIZES.put(size(250, 600), 179);
        SIZES.put(size(600, 300), 195);
        SIZES.put(size(640, 360), 198);
        SIZES.put(size(640, 200), 199);
        SIZES.put(size(1030, 590), 213);
        SIZES.put(size(980, 360), 214);
        SIZES.put(size(320, 180), 229);
        SIZES.put(size(2000, 1400), 230);
        SIZES.put(size(580, 400), 232);
        SIZES.put(size(480, 820), 256);
        SIZES.put(size(400, 600), 257);
        SIZES.put(size(500, 200), 258);
        SIZES.put(size(998, 200), 259);
        SIZES.put(size(970, 1000), 264);
        SIZES.put(size(1920, 1080), 265);
        SIZES.put(size(1800, 200), 274);
        SIZES.put(size(320, 500), 278);
        SIZES.put(size(320, 400), 282);
        SIZES.put(size(640, 380), 288);
        SIZES.put(size(500, 1000), 548);
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

    static Integer toId(Format size) {
        return SIZES.getOrDefault(size(size.getW(), size.getH()), 0);
    }

    /**
     * Returns {@link Format}s corresponding to the given size IDs or empty if no one matched.
     */
    static List<Format> idToSize(List<Integer> sizeIds) {
        final List<Format> result = new ArrayList<>();
        for (Integer id : sizeIds) {
            final List<Format> idResult = SIZES.keySet().stream()
                    .filter(rubiconSize -> SIZES.get(rubiconSize).equals(id))
                    // sort and limit to avoid conflicts when one value(id) corresponds to multiple keys(sizes)
                    .sorted(Comparator.comparing(rubiconSize -> rubiconSize.h))
                    .limit(1)
                    .map(rubiconSize -> Format.builder().w(rubiconSize.w).h(rubiconSize.h).build())
                    .collect(Collectors.toList());
            result.addAll(idResult);
        }
        return result;
    }

    // MAS comparator stuff

    /**
     * This comparator is used to sort size ids with intention to pick primary one. Sort order must be 15,2,9 in that
     * order, then ascending order.
     */
    static Comparator<Integer> comparator() {
        return COMPARATOR;
    }

    private static class MASComparator implements Comparator<Integer> {

        @Override
        public int compare(Integer o1, Integer o2) {
            return ordinal(o1) - ordinal(o2);
        }

        private static int ordinal(Integer i) {
            switch (i) {
                case 15:
                    return 1;
                case 2:
                    return 2;
                case 9:
                    return 3;
                default:
                    return 4;
            }
        }
    }
}
