package org.prebid.server.bidder.adhese.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.*;

import static com.fasterxml.jackson.annotation.JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY;

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

    @JsonUnwrapped
    @JsonFormat(with = ACCEPT_SINGLE_VALUE_AS_ARRAY)
    public Map<String, List<String>> parameters = new TreeMap<>();

    @Override
    public String toString() {
        return "RequestBody{" +
                "slots=" + slots +
                ", parameters=" + parameters +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdheseRequestBody)) return false;
        AdheseRequestBody that = (AdheseRequestBody) o;
        return Objects.equals(slots, that.slots) &&
                Objects.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slots, parameters);
    }
}
