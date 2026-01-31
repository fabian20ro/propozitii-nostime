CREATE TABLE IF NOT EXISTS words (
    id           SERIAL PRIMARY KEY,
    word         VARCHAR(50) NOT NULL,
    type         CHAR(1) NOT NULL,
    gender       CHAR(1),
    syllables    SMALLINT NOT NULL,
    rhyme        VARCHAR(10) NOT NULL,
    first_letter CHAR(1) NOT NULL,
    articulated  VARCHAR(60),
    feminine     VARCHAR(60)
);

CREATE INDEX IF NOT EXISTS idx_words_syllables ON words(type, syllables);
CREATE INDEX IF NOT EXISTS idx_words_first_letter ON words(type, first_letter);
CREATE INDEX IF NOT EXISTS idx_words_type_rhyme_syllables ON words(type, rhyme, syllables);
