package de.marvin.playtime.core.database;

import de.marvin.playtime.core.session.Session;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the result of loading a {@link Session} from a data source.
 *
 * @param session {@link Session} which was loaded
 * @param source {@link DataSource} the {@link Session} was loaded from
 */
public record SessionLoadResult(
    @NotNull Session session,
    @NotNull DataSource source
) {

    /**
     * Creates a new {@link SessionLoadResult}.
     *
     * @param session {@link Session} which was loaded
     * @param source {@link DataSource} the {@link Session} was loaded from
     * @return New {@link SessionLoadResult} instance
     */
    static SessionLoadResult of(
            @NotNull Session session,
            @NotNull DataSource source
    ) {
        return new SessionLoadResult(session, source);
    }

    /**
     * Represents the source from which a {@link Session} was loaded.
     */
    public enum DataSource {
        SQL,
        REDIS
    }

}
