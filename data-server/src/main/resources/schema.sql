CREATE TABLE IF NOT EXISTS party (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  join_code   CHAR(6)      NOT NULL,
  name        VARCHAR(100) NOT NULL,
  created_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT uq_party_join_code UNIQUE (join_code)
);

CREATE TABLE IF NOT EXISTS guest (
  id          CHAR(36)     PRIMARY KEY,
  party_id    BIGINT       NOT NULL,
  nickname    VARCHAR(30)  NOT NULL,
  role        VARCHAR(10)  NOT NULL,
  joined_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_guest_party FOREIGN KEY (party_id) REFERENCES party(id) ON DELETE CASCADE,
  CONSTRAINT uq_guest_nickname UNIQUE (party_id, nickname),
  CONSTRAINT ck_guest_role CHECK (role IN ('HOST', 'GUEST'))
);

CREATE TABLE IF NOT EXISTS queue_item (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  party_id          BIGINT       NOT NULL,
  spotify_track_id  VARCHAR(64)  NOT NULL,
  title             VARCHAR(300) NOT NULL,
  artist            VARCHAR(300) NOT NULL,
  album             VARCHAR(300) NOT NULL,
  duration_ms       INT          NOT NULL,
  added_by          CHAR(36)     NOT NULL,
  added_at          TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_item_party FOREIGN KEY (party_id) REFERENCES party(id) ON DELETE CASCADE,
  CONSTRAINT fk_item_guest FOREIGN KEY (added_by) REFERENCES guest(id),
  CONSTRAINT uq_item_track UNIQUE (party_id, spotify_track_id)
);

CREATE TABLE IF NOT EXISTS vote (
  queue_item_id  BIGINT       NOT NULL,
  guest_id       CHAR(36)     NOT NULL,
  vote_value     SMALLINT     NOT NULL,
  updated_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (queue_item_id, guest_id),
  CONSTRAINT fk_vote_item  FOREIGN KEY (queue_item_id) REFERENCES queue_item(id) ON DELETE CASCADE,
  CONSTRAINT fk_vote_guest FOREIGN KEY (guest_id) REFERENCES guest(id) ON DELETE CASCADE,
  CONSTRAINT ck_vote_value CHECK (vote_value IN (-1, 1))
);
