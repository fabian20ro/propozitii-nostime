ALTER TABLE words ADD COLUMN feminine_syllables SMALLINT;

CREATE INDEX IF NOT EXISTS idx_words_type_rarity_feminine_syllables
    ON words(type, rarity_level, feminine_syllables);
