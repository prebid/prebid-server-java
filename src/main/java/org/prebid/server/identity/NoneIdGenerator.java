package org.prebid.server.identity;

/**
 * Returns ID as null.
 */
public class NoneIdGenerator implements IdGenerator {

    @Override
    public String generateId() {
        return null;
    }

    @Override
    public IdGeneratorType getType() {
        return IdGeneratorType.none;
    }
}
