package de.marvin.playtime.core.database;

import de.marvin.api.core.Cloud;
import de.marvin.api.core.database.Database;
import de.marvin.api.core.utils.CloudFuture;
import de.marvin.playtime.core.session.Session;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.logging.Logger;

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
     * Fetches the {@link Session} of a player by their {@link UUID}.
     * <p>
     * <b>Note:</b> If the player does not exist in the database,
     * a new {@link Session} with default values is returned.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@link CloudFuture} containing the player's {@link Session}.
     */
    public CloudFuture<Session> session(
            @NotNull UUID uniqueId
    ) {
        return this.database.queryResult(
                        "SELECT * FROM " + this.table + " WHERE unique_id = ?;",
                        preparedStatement -> preparedStatement.setString(1, uniqueId.toString())
                )
                .map(resultSet -> {
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
                                "Failed to fetch session data for player " + uniqueId + ": "
                                        + exception.getMessage()
                        );
                    }
                    return Session.defaultSession(uniqueId);
                });
    }

    /**
     * Safely updates playtime and onlinetime of the player with the given {@link UUID}.
     * If the player does not exist in the database, a new entry is created.
     * <p>
     * This method ensures that onlinetime and playtime are only increased
     * and never decreased. If an attempt is made to decrease either value,
     * the operation is aborted and a warning is logged.
     *
     * @param uniqueId   {@link UUID} of the player
     * @param onlinetime onlinetime to update in milliseconds
     * @param playtime   playtime to update in milliseconds
     */
    public void safeUpdate(
            @NotNull UUID uniqueId,
            @Nullable Long onlinetime,
            @Nullable Long playtime
    ) {
        this.database.queryResult(
                "SELECT onlinetime, playtime FROM " + this.table + " WHERE unique_id = ? LIMIT 1",
                preparedStatement -> preparedStatement.setString(1, uniqueId.toString())
        ).onSuccess(resultSet -> {
            try {
                if (!resultSet.next()) {
                    this.update(uniqueId, onlinetime, playtime);
                    return;
                }
                long currentOnlinetime = resultSet.getLong("onlinetime");
                long currentPlaytime = resultSet.getLong("playtime");
                if (onlinetime == null || currentOnlinetime > onlinetime) {
                    this.logger.warning(
                            "Attempted to decrease onlinetime for player " + uniqueId +
                                    " from " + currentOnlinetime + " to " + onlinetime + ". Operation aborted."
                    );
                    return;
                }
                if (playtime == null || currentPlaytime > playtime) {
                    this.logger.warning(
                            "Attempted to decrease playtime for player " + uniqueId +
                                    " from " + currentPlaytime + " to " + playtime + ". Operation aborted."
                    );
                    return;
                }
                this.update(uniqueId, onlinetime, playtime);
            } catch (Exception exception) {
                this.logger.warning(
                        "Failed to update session data for player " + uniqueId + ": " + exception.getMessage()
                );
            }
        });
    }

    /**
     * Updates playtime and onlinetime of the player with the given {@link UUID}.
     * If the player does not exist in the database, a new entry is created.
     *
     * @param uniqueId   {@link UUID} of the player
     * @param onlinetime onlinetime to update in milliseconds
     * @param playtime   playtime to update in milliseconds
     */
    public void update(
            @NotNull UUID uniqueId,
            @Nullable Long onlinetime,
            @Nullable Long playtime
    ) {
        this.database.update(
                "INSERT INTO " + this.table + " (unique_id, onlinetime, playtime) " +
                        "VALUES (?, COALESCE(?, 0), COALESCE(?, 0)) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "onlinetime = COALESCE(VALUES(onlinetime), onlinetime), " +
                        "playtime = COALESCE(VALUES(playtime), playtime);",
                preparedStatement -> {
                    preparedStatement.setString(1, uniqueId.toString());
                    preparedStatement.setObject(2, onlinetime, java.sql.Types.BIGINT);
                    preparedStatement.setObject(3, playtime, java.sql.Types.BIGINT);
                }
        );
    }

    /**
     * Deletes the session data of the player with the given {@link UUID}
     * from the database.
     *
     * @param uniqueId {@link UUID} of the player
     */
    public void delete(
            @NotNull UUID uniqueId
    ) {
        this.database.update(
                "DELETE FROM " + this.table + " WHERE unique_id = ?;",
                preparedStatement -> preparedStatement.setString(1, uniqueId.toString())
        );
    }

}
