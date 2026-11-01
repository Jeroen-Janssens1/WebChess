-- MySQL 8+

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";

USE dbname;

-- Clean start (optional)
DROP TABLE IF EXISTS games;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
  id INT NOT NULL AUTO_INCREMENT,
  username VARCHAR(255) NOT NULL,
  password VARCHAR(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_username (username)
) ENGINE=InnoDB;

CREATE TABLE games (
  id VARCHAR(36) NOT NULL,
  white_username VARCHAR(255) NOT NULL,
  black_username VARCHAR(255) NOT NULL,
  result VARCHAR(7) NOT NULL,
  played_at DATETIME NOT NULL,
  pgn MEDIUMTEXT NOT NULL,
  PRIMARY KEY (id),
  KEY idx_games_white_history (white_username, played_at),
  KEY idx_games_black_history (black_username, played_at)
) ENGINE=InnoDB;