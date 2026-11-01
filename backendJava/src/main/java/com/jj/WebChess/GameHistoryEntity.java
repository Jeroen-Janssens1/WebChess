package com.jj.WebChess;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "games")
public class GameHistoryEntity {
    private static final Pattern TERMINATION_TAG =
        Pattern.compile("\\[Termination \"([^\"]+)\"\\]");

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "white_username", nullable = false, length = 255)
    private String whiteUsername;

    @Column(name = "black_username", nullable = false, length = 255)
    private String blackUsername;

    @Column(name = "result", nullable = false, length = 7)
    private String result;

    @Column(name = "played_at", nullable = false, columnDefinition = "DATETIME")
    private LocalDateTime playedAt;

    @Column(name = "pgn", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String pgn;

    protected GameHistoryEntity() {}

    public GameHistoryEntity(
            String id, String whiteUsername, String blackUsername, String result,
            LocalDateTime playedAt, String pgn) {
        this.id = id;
        this.whiteUsername = whiteUsername;
        this.blackUsername = blackUsername;
        this.result = result;
        this.playedAt = playedAt;
        this.pgn = pgn;
    }

    public String getId() {
        return id;
    }

    public String getWhiteUsername() {
        return whiteUsername;
    }

    public String getBlackUsername() {
        return blackUsername;
    }

    public String getResult() {
        return result;
    }

    public LocalDateTime getPlayedAt() {
        return playedAt;
    }

    public String getPgn() {
        return pgn;
    }

    public String getTermination() {
        Matcher matcher = TERMINATION_TAG.matcher(pgn);
        return matcher.find() ? matcher.group(1) : result.equals("1/2-1/2") ? "DRAW" : "UNKNOWN";
    }
}
