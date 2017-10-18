package org.rtb.vexing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(makeFinal = true)
public class UidsCookie {

    public static final String COOKIE_NAME = "uids";

    private Map<String, String> uids;

    public String bday;

    public String uidFrom(String bidderCode) {
        return uids.get(bidderCode);
    }

    public boolean hasUidFrom(String bidderCode) {
        return uids.containsKey(bidderCode);
    }
}
