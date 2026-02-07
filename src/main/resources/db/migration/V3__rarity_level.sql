ALTER TABLE words
    ADD COLUMN IF NOT EXISTS rarity_level SMALLINT NOT NULL DEFAULT 4,
    ADD CONSTRAINT chk_words_rarity_level CHECK (rarity_level BETWEEN 1 AND 5);

CREATE INDEX IF NOT EXISTS idx_words_type_rarity_syllables
    ON words(type, rarity_level, syllables);

CREATE INDEX IF NOT EXISTS idx_words_type_rarity_rhyme
    ON words(type, rarity_level, rhyme);

CREATE INDEX IF NOT EXISTS idx_words_type_rarity_articulated_syllables
    ON words(type, rarity_level, articulated_syllables);

CREATE INDEX IF NOT EXISTS idx_words_type_rarity_first_letter
    ON words(type, rarity_level, first_letter);
