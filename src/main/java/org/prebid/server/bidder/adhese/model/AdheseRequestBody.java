package org.prebid.server.bidder.adhese.model;

import java.util.*;

public class AdheseRequestBody {
    public static class Slot {

        public static Slot create(String slotname) {
            Slot slot = new Slot();
            slot.slotname = slotname;
            return slot;
        }

        private String slotname;

        public String getSlotname() {
            return slotname;
        }

        @Override
        public String toString() {
            return "slot: " + slotname;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Slot)) return false;
            Slot slot = (Slot) o;
            return Objects.equals(slotname, slot.slotname);
        }

        @Override
        public int hashCode() {
            return Objects.hash(slotname);
        }
    }

    public List<Slot> slots = new ArrayList<>();

    public Map<String, List<String>> parameters = new TreeMap<>();

    @Override
    public String toString() {
        return "RequestBody{" +
                "slots=" + slots +
                ", parameters=" + parameters +
                '}';
    }
}
