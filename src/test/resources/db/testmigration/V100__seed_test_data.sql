-- Test seed data for integration tests
-- Words chosen to satisfy all provider constraints:
--   CoupletProvider: 4+ nouns sharing a rhyme
--   HaikuProvider: nouns with 5-syllable articulated forms, 3-syllable adj/verb
--   TautogramProvider: nouns, adjectives, verbs sharing two-letter prefix 'ma'
--   MirrorProvider: 2 rhyme groups with 2+ nouns each
--   ComparisonProvider / DefinitionProvider: basic variety

-- Nouns (type='N')
-- Rhyme group 'asă' (feminine, 4 nouns for CoupletProvider)
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine, articulated_syllables) VALUES
  ('masă',    'N', 'F', 2, 'asă', 'm', 'masa',       NULL, 2),
  ('casă',    'N', 'F', 2, 'asă', 'c', 'casa',       NULL, 2),
  ('basă',    'N', 'F', 2, 'asă', 'b', 'basa',       NULL, 2),
  ('rasă',    'N', 'F', 2, 'asă', 'r', 'rasa',       NULL, 2);

-- Rhyme group 'ine' (masculine, 2+ nouns for MirrorProvider second group)
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine, articulated_syllables) VALUES
  ('câine',   'N', 'M', 2, 'ine', 'c', 'câinele',    NULL, 3),
  ('mine',    'N', 'M', 2, 'ine', 'm', 'minele',     NULL, 3);

-- Nouns with 5-syllable articulated forms (for HaikuProvider)
-- 'călătorie' F (5 syl), articulated='călătoria' (5 syl: că-lă-to-ri-a)
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine, articulated_syllables) VALUES
  ('călătorie', 'N', 'F', 5, 'rie', 'c', 'călătoria', NULL, 5);

-- More nouns for variety, starting with 'm' for TautogramProvider
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine, articulated_syllables) VALUES
  ('munte',   'N', 'M', 2, 'nte', 'm', 'muntele',    NULL, 3),
  ('mare',    'N', 'F', 2, 'are', 'm', 'marea',       NULL, 2);

-- Another noun with 5-syllable articulated form and rhyme 'ria' to match călătoria
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine, articulated_syllables) VALUES
  ('întâmplare', 'N', 'F', 4, 'are', 'î', 'întâmplarea', NULL, 5);

-- Adjectives (type='A')
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine, articulated_syllables) VALUES
  ('mare',    'A', NULL, 2, 'are', 'm', NULL, 'mare',       NULL),
  ('frumos',  'A', NULL, 2, 'mos', 'f', NULL, 'frumoasă',   NULL),
  ('minunat', 'A', NULL, 3, 'nat', 'm', NULL, 'minunată',   NULL),
  ('blând',   'A', NULL, 1, 'ând', 'b', NULL, 'blândă',     NULL),
  ('miraculos','A', NULL, 4, 'los', 'm', NULL, 'miraculoasă', NULL);

-- Verbs (type='V')
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine, articulated_syllables) VALUES
  ('marchează','V', NULL, 3, 'ază', 'm', NULL, NULL, NULL),
  ('merge',    'V', NULL, 2, 'rge', 'm', NULL, NULL, NULL),
  ('aleargă',  'V', NULL, 3, 'rgă', 'a', NULL, NULL, NULL),
  ('mănâncă',  'V', NULL, 3, 'ncă', 'm', NULL, NULL, NULL),
  ('colindă',  'V', NULL, 3, 'ndă', 'c', NULL, NULL, NULL),
  ('bate',     'V', NULL, 2, 'ate', 'b', NULL, NULL, NULL);
