-- Test seed data for integration tests
-- Words chosen to satisfy all provider constraints at rarity=2.
-- At rarity=1, some constrained providers should become unsatisfiable.

-- Nouns (type='N')
-- Rhyme group 'asă' (feminine, 4 nouns for MirrorProvider ABBA)
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine, articulated_syllables, rarity_level, feminine_syllables) VALUES
  ('masă',    'N', 'F', 2, 'asă', 'm', 'masa',       NULL, 2, 1, NULL),
  ('casă',    'N', 'F', 2, 'asă', 'c', 'casa',       NULL, 2, 1, NULL),
  ('basă',    'N', 'F', 2, 'asă', 'b', 'basa',       NULL, 2, 2, NULL),
  ('rasă',    'N', 'F', 2, 'asă', 'r', 'rasa',       NULL, 2, 2, NULL);

-- Rhyme group 'ine' (masculine, 3 nouns for MirrorProvider ABBA)
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine, articulated_syllables, rarity_level, feminine_syllables) VALUES
  ('câine',   'N', 'M', 2, 'ine', 'c', 'câinele',    NULL, 3, 1, NULL),
  ('mine',    'N', 'M', 2, 'ine', 'm', 'minele',     NULL, 3, 2, NULL),
  ('vine',    'N', 'M', 2, 'ine', 'v', 'vinele',     NULL, 3, 2, NULL);

-- Nouns with 5-syllable articulated forms (for HaikuProvider)
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine, articulated_syllables, rarity_level, feminine_syllables) VALUES
  ('călătorie', 'N', 'F', 5, 'rie', 'c', 'călătoria', NULL, 5, 2, NULL);

-- More nouns for variety, including a second 'ma' noun for TautogramProvider
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine, articulated_syllables, rarity_level, feminine_syllables) VALUES
  ('munte',   'N', 'M', 2, 'nte', 'm', 'muntele',     NULL, 3, 2, NULL),
  ('mare',    'N', 'F', 2, 'are', 'm', 'marea',       NULL, 2, 2, NULL);

-- Another noun with 5-syllable articulated form
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine, articulated_syllables, rarity_level, feminine_syllables) VALUES
  ('întâmplare', 'N', 'F', 4, 'are', 'î', 'întâmplarea', NULL, 5, 2, NULL);

-- Adjectives (type='A')
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine, articulated_syllables, rarity_level, feminine_syllables) VALUES
  ('mare',     'A', NULL, 2, 'are', 'm', NULL, 'mare',         NULL, 1, 2),
  ('frumos',   'A', NULL, 2, 'mos', 'f', NULL, 'frumoasă',     NULL, 1, 3),
  ('minunat',  'A', NULL, 3, 'nat', 'm', NULL, 'minunată',     NULL, 2, 4),
  ('blând',    'A', NULL, 1, 'ând', 'b', NULL, 'blândă',       NULL, 2, 2),
  ('miraculos','A', NULL, 4, 'los', 'm', NULL, 'miraculoasă',  NULL, 2, 5);

-- Verbs (type='V')
-- Rhyme group 'ază' (2 verbs for MirrorProvider ABBA)
-- Rhyme group 'ndă' (2 verbs for MirrorProvider ABBA)
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine, articulated_syllables, rarity_level, feminine_syllables) VALUES
  ('marchează','V', NULL, 3, 'ază', 'm', NULL, NULL, NULL, 2, NULL),
  ('lucrează', 'V', NULL, 3, 'ază', 'l', NULL, NULL, NULL, 2, NULL),
  ('merge',    'V', NULL, 2, 'rge', 'm', NULL, NULL, NULL, 1, NULL),
  ('aleargă',  'V', NULL, 3, 'rgă', 'a', NULL, NULL, NULL, 2, NULL),
  ('mănâncă',  'V', NULL, 3, 'ncă', 'm', NULL, NULL, NULL, 2, NULL),
  ('colindă',  'V', NULL, 3, 'ndă', 'c', NULL, NULL, NULL, 2, NULL),
  ('prindă',   'V', NULL, 2, 'ndă', 'p', NULL, NULL, NULL, 2, NULL),
  ('bate',     'V', NULL, 2, 'ate', 'b', NULL, NULL, NULL, 1, NULL);
