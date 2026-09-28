-- movies / users / ratings with denormalized avg_rating in movies
-- MySQL 8+

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";

USE dbname;

-- Clean start (optional)
 DROP TABLE IF EXISTS users;

CREATE TABLE users (
  id INT NOT NULL AUTO_INCREMENT,
  username VARCHAR(255) NOT NULL,
  password VARCHAR(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_username (username)
) ENGINE=InnoDB;