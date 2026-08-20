package de.marvin.playtime.core.database.connection;

import de.marvin.api.core.Cloud;
import de.marvin.api.core.database.Database;
import de.marvin.api.core.database.PreparedStatementFunction;
import de.marvin.api.core.utils.CloudFuture;
import de.marvin.playtime.core.session.Session;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Types;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Handles the communication with the SQL database.
 */
public class SQLConnection {

    private final Logger logger;
    private final Database database;

    private final String table;

    public SQLConnection(
            @NotNull Logger logger,
            @NotNull String table
    ) {
        this.logger = logger;
        this.database = Cloud.database();
        this.table = table;

        this.database.update(
                "CREATE TABLE IF NOT EXISTS " + table + " (" +
                        "unique_id VARCHAR(36) PRIMARY KEY, " +
                        "onlinetime BIGINT DEFAULT 0 CHECK (onlinetime >= 0), " +
                        "playtime BIGINT DEFAULT 0 CHECK (playtime >= 0 AND playtime <= onlinetime)" +
                        ");"
        ).onFailure(Throwable::printStackTrace);
    }

    /**
     * Fetches the {@link Session} of the given player.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@link CloudFuture} containing the {@link Session}, or {@code null} if not found
     */
    public CloudFuture<@Nullable Session> session(
            @NotNull UUID uniqueId
    ) {
        return this.database.queryResult(
                "SELECT * FROM " + this.table + " WHERE unique_id = ?;",
                preparedStatement -> preparedStatement.setString(1, uniqueId.toString())
        ).map(resultSet -> {
            try {
                if (resultSet.next()) {
                    long onlinetime = resultSet.getLong("onlinetime");
                    long playtime = resultSet.getLong("playtime");
                    return new Session(
                            uniqueId,
                            onlinetime,
                            playtime
                    );
                }
            } catch (Exception exception) {
                this.logger.warning(
                        "Failed to fetch session data for player " + uniqueId + ": " + exception.getMessage()
                );
            }
            return null;
        });
    }

    /**
     * Safely updates playtime and onlinetime of the player. If the player does not exist in the database, a
     * new entry is created.
     * <p>
     * This method ensures that onlinetime and playtime are only increased and never decreased. A {@code null}
     * value leaves the corresponding existing value unchanged.
     *
     * @param uniqueId   {@link UUID} of the player
     * @param onlinetime Onlinetime to update in milliseconds, or {@code null} to keep the current value
     * @param playtime   Playtime to update in milliseconds, or {@code null} to keep the current value
     * @return {@link CloudFuture} that completes when the operation is finished
     */
    public @NotNull CloudFuture<Void> safeUpdate(
            @NotNull UUID uniqueId,
            @Nullable Long onlinetime,
            @Nullable Long playtime
    ) {
        return this.database.update(
                "INSERT INTO " + this.table + " (unique_id, onlinetime, playtime) " +
                        "VALUES (?, COALESCE(?, 0), COALESCE(?, 0)) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "onlinetime = GREATEST(onlinetime, COALESCE(?, onlinetime)), " +
                        "playtime = GREATEST(playtime, COALESCE(?, playtime));",
                updateStatement(uniqueId, onlinetime, playtime)
        );
    }

    /**
     * Updates playtime and onlinetime of the player. If the player does not exist in the database, a new entry
     * is created.
     *
     * @param uniqueId   {@link UUID} of the player
     * @param onlinetime onlinetime to update in milliseconds, or {@code null} to keep the current value
     * @param playtime   playtime to update in milliseconds, or {@code null} to keep the current value
     * @return {@link CloudFuture} that completes when the operation is finished
     */
    public CloudFuture<Void> update(
            @NotNull UUID uniqueId,
            @Nullable Long onlinetime,
            @Nullable Long playtime
    ) {
        return this.database.update(
                "INSERT INTO " + this.table + " (unique_id, onlinetime, playtime) " +
                        "VALUES (?, COALESCE(?, 0), COALESCE(?, 0)) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "onlinetime = COALESCE(?, onlinetime), " +
                        "playtime = COALESCE(?, playtime);",
                updateStatement(uniqueId, onlinetime, playtime)
        );
    }

    /**
     * Deletes the session data of the player with the given {@link UUID} from the database.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@link CloudFuture} that completes when the operation is finished
     */
    public CloudFuture<Void> delete(
            @NotNull UUID uniqueId
    ) {
        return this.database.update(
                "DELETE FROM " + this.table + " WHERE unique_id = ?;",
                preparedStatement -> preparedStatement.setString(1, uniqueId.toString())
        );
    }

    /**
     * Prepares a {@link PreparedStatementFunction} with parameters for updating the given player's session
     * data.
     *
     * @param uniqueId   {@link UUID} of the player
     * @param onlinetime Onlinetime to update in milliseconds, or {@code null} to keep the current value
     * @param playtime   Playtime to update in milliseconds, or {@code null} to keep the current value
     * @return {@link PreparedStatementFunction} with parameters for updating the given player's session data
     */
    private static @NotNull PreparedStatementFunction updateStatement(
            @NotNull UUID uniqueId,
            @Nullable Long onlinetime,
            @Nullable Long playtime
    ) {
        return preparedStatement -> {
            preparedStatement.setString(1, uniqueId.toString());
            preparedStatement.setObject(2, onlinetime, Types.BIGINT);
            preparedStatement.setObject(3, playtime, Types.BIGINT);
            preparedStatement.setObject(4, onlinetime, Types.BIGINT);
            preparedStatement.setObject(5, playtime, Types.BIGINT);
        };
    }

}
