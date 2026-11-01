package com.jj.WebChess;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface GameHistoryRepository extends JpaRepository<GameHistoryEntity, String> {

    @Query("""
        SELECT game FROM GameHistoryEntity game
        WHERE game.id = :gameId
          AND (game.whiteUsername = :username OR game.blackUsername = :username)
        """)
    Optional<GameHistoryEntity> findPlayerGame(
        @Param("gameId") String gameId, @Param("username") String username);

    @Query("""
        SELECT game FROM GameHistoryEntity game
        WHERE (game.whiteUsername = :username OR game.blackUsername = :username)
          AND (:playerName IS NULL
            OR LOWER(game.whiteUsername) LIKE LOWER(CONCAT('%', :playerName, '%'))
            OR LOWER(game.blackUsername) LIKE LOWER(CONCAT('%', :playerName, '%')))
          AND (:fromTime IS NULL OR game.playedAt >= :fromTime)
          AND (:toTime IS NULL OR game.playedAt < :toTime)
          AND (:color IS NULL
            OR (:color = 'WHITE' AND game.whiteUsername = :username)
            OR (:color = 'BLACK' AND game.blackUsername = :username))
          AND (:condition IS NULL
            OR (:condition = 'CHECKMATE' AND game.pgn LIKE '%[Termination "CHECKMATE"]%')
            OR (:condition = 'FORFEIT' AND game.pgn LIKE '%[Termination "FORFEIT"]%')
            OR (:condition = 'STALEMATE' AND game.pgn LIKE '%[Termination "STALEMATE"]%')
            OR (:condition = 'DRAW'
              AND game.result = '1/2-1/2'
              AND game.pgn NOT LIKE '%[Termination "STALEMATE"]%'))
        ORDER BY game.playedAt DESC, game.id DESC
        """)
    Page<GameHistoryEntity> findPlayerHistory(
        @Param("username") String username,
        @Param("playerName") String playerName,
        @Param("fromTime") LocalDateTime fromTime,
        @Param("toTime") LocalDateTime toTime,
        @Param("color") String color,
        @Param("condition") String condition,
        Pageable pageable);
}
