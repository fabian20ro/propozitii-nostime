-- Test seed data for integration tests
-- Words chosen to satisfy all provider constraints at rarity=2.
-- At rarity=1, some constrained providers should become unsatisfiable.

-- Nouns (type='N')
-- Rhyme group 'asă' (feminine, 4 nouns for MirrorProvider ABBA)
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine, articulated_syllables, rarity_level) VALUES
  ('masă',    'N', 'F', 2, 'asă', 'm', 'masa',       NULL, 2, 1),
  ('casă',    'N', 'F', 2, 'asă', 'c', 'casa',       NULL, 2, 1),
  ('basă',    'N', 'F', 2, 'asă', 'b', 'basa',       NULL, 2, 2),
  ('rasă',    'N', 'F', 2, 'asă', 'r', 'rasa',       NULL, 2, 2);

-- Rhyme group 'ine' (masculine, 3 nouns for MirrorProvider ABBA)
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine, articulated_syllables, rarity_level) VALUES
  ('câine',   'N', 'M', 2, 'ine', 'c', 'câinele',    NULL, 3, 1),
  ('mine',    'N', 'M', 2, 'ine', 'm', 'minele',     NULL, 3, 2),
  ('vine',    'N', 'M', 2, 'ine', 'v', 'vinele',     NULL, 3, 2);

-- Nouns with 5-syllable articulated forms (for HaikuProvider)
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine, articulated_syllables, rarity_level) VALUES
  ('călătorie', 'N', 'F', 5, 'rie', 'c', 'călătoria', NULL, 5, 2);

-- More nouns for variety, including a second 'ma' noun for TautogramProvider
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine, articulated_syllables, rarity_level) VALUES
  ('munte',   'N', 'M', 2, 'nte', 'm', 'muntele',     NULL, 3, 2),
  ('mare',    'N', 'F', 2, 'are', 'm', 'marea',       NULL, 2, 2);

-- Another noun with 5-syllable articulated form
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine, articulated_syllables, rarity_level) VALUES
  ('întâmplare', 'N', 'F', 4, 'are', 'î', 'întâmplarea', NULL, 5, 2);

-- Adjectives (type='A')
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine, articulated_syllables, rarity_level) VALUES
  ('mare',     'A', NULL, 2, 'are', 'm', NULL, 'mare',         NULL, 1),
  ('frumos',   'A', NULL, 2, 'mos', 'f', NULL, 'frumoasă',     NULL, 1),
  ('minunat',  'A', NULL, 3, 'nat', 'm', NULL, 'minunată',     NULL, 2),
  ('blând',    'A', NULL, 1, 'ând', 'b', NULL, 'blândă',       NULL, 2),
  ('miraculos','A', NULL, 4, 'los', 'm', NULL, 'miraculoasă',  NULL, 2);

-- Verbs (type='V')
-- Rhyme group 'ază' (2 verbs for MirrorProvider ABBA)
-- Rhyme group 'ndă' (2 verbs for MirrorProvider ABBA)
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine, articulated_syllables, rarity_level) VALUES
  ('marchează','V', NULL, 3, 'ază', 'm', NULL, NULL, NULL, 2),
  ('lucrează', 'V', NULL, 3, 'ază', 'l', NULL, NULL, NULL, 2),
  ('merge',    'V', NULL, 2, 'rge', 'm', NULL, NULL, NULL, 1),
  ('aleargă',  'V', NULL, 3, 'rgă', 'a', NULL, NULL, NULL, 2),
  ('mănâncă',  'V', NULL, 3, 'ncă', 'm', NULL, NULL, NULL, 2),
  ('colindă',  'V', NULL, 3, 'ndă', 'c', NULL, NULL, NULL, 2),
  ('prindă',   'V', NULL, 2, 'ndă', 'p', NULL, NULL, NULL, 2),
  ('bate',     'V', NULL, 2, 'ate', 'b', NULL, NULL, NULL, 1);
