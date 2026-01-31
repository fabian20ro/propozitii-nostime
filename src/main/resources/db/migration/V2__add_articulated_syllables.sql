ALTER TABLE words ADD COLUMN articulated_syllables SMALLINT;

CREATE INDEX idx_words_articulated_syllables ON words(type, articulated_syllables);
CREATE INDEX idx_words_type ON words(type);
