package org.prebid.server.bidder.adhese.model;

import lombok.Getter;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Value
public class AdheseRequestBody {

    public static class Slot {

        public static Slot create(String slotname) {
            Slot slot = new Slot();
            slot.slotname = slotname;
            return slot;
        }

        @Getter
        private String slotname;

        @Override
        public String toString() {
            return "slot: " + slotname;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Slot)) {
                return false;
            }
            Slot slot = (Slot) o;
            return Objects.equals(slotname, slot.slotname);
        }

        @Override
        public int hashCode() {
            return Objects.hash(slotname);
        }
    }

    List<Slot> slots;

    Map<String, List<String>> parameters;

    public AdheseRequestBody(List<Slot> slots, Map<String, List<String>> parameters) {
        this.slots = slots;
        this.parameters = parameters;
    }

    @Override
    public String toString() {
        return "RequestBody{"
                + "slots=" + slots
                + ", parameters=" + parameters
                + '}';
    }
}
