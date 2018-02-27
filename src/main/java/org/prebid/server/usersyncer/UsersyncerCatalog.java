package org.prebid.server.usersyncer;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides simple access to all usersyncers registered so far.
 */
public class UsersyncerCatalog {

    private final Map<String, Usersyncer> usersyncers;

    public UsersyncerCatalog(List<Usersyncer> usersyncers) {
        this.usersyncers = Objects.requireNonNull(usersyncers).stream()
                .collect(Collectors.toMap(Usersyncer::name, Function.identity()));
    }

    /**
     * Returns a {@link Usersyncer} registered by the given name or null if there is none.
     * <p>
     * Therefore this method should be called only for names that previously passed validity check
     * through calling {@link #isValidName(String)}.
     */
    public Usersyncer byName(String name) {
        return usersyncers.get(name);
    }

    /**
     * Tells if given name corresponds to any of the registered usersyncers.
     */
    public boolean isValidName(String name) {
        return usersyncers.containsKey(name);
    }
}
