package org.prebid.server.identity;

import java.util.UUID;

/**
 * Returns ID as {@link UUID} string.
 */
public class UUIDIdGenerator implements IdGenerator {

    @Override
    public String generateId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public IdGeneratorType getType() {
        return IdGeneratorType.uuid;
    }
}
