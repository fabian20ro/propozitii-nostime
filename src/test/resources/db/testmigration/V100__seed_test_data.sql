-- Test seed data for integration tests
-- Words chosen to satisfy all provider constraints:
--   CoupletProvider: 4+ nouns sharing a rhyme
--   HaikuProvider: nouns with 5-syllable articulated forms, 3-syllable adj/verb
--   TautogramProvider: nouns, adjectives, verbs sharing two-letter prefix 'ma'
--   MirrorProvider: 2 rhyme groups with 2+ nouns each
--   ComparisonProvider / DefinitionProvider: basic variety

-- Nouns (type='N')
-- Rhyme group 'asă' (feminine, 4 nouns for CoupletProvider)
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine) VALUES
  ('masă',    'N', 'F', 2, 'asă', 'm', 'masa',       NULL),
  ('casă',    'N', 'F', 2, 'asă', 'c', 'casa',       NULL),
  ('basă',    'N', 'F', 2, 'asă', 'b', 'basa',       NULL),
  ('rasă',    'N', 'F', 2, 'asă', 'r', 'rasa',       NULL);

-- Rhyme group 'ine' (masculine, 2+ nouns for MirrorProvider second group)
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine) VALUES
  ('câine',   'N', 'M', 2, 'ine', 'c', 'câinele',    NULL),
  ('mine',    'N', 'M', 2, 'ine', 'm', 'minele',     NULL);

-- Nouns with 5-syllable articulated forms (for HaikuProvider)
-- 'călătorie' F (5 syl), articulated='călătoria' (5 syl: că-lă-to-ri-a)
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine) VALUES
  ('călătorie', 'N', 'F', 5, 'rie', 'c', 'călătoria', NULL);

-- More nouns for variety, starting with 'm' for TautogramProvider
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine) VALUES
  ('munte',   'N', 'M', 2, 'nte', 'm', 'muntele',    NULL),
  ('mare',    'N', 'F', 2, 'are', 'm', 'marea',       NULL);

-- Another noun with 5-syllable articulated form and rhyme 'ria' to match călătoria
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine) VALUES
  ('întâmplare', 'N', 'F', 4, 'are', 'î', 'întâmplarea', NULL);

-- Adjectives (type='A')
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine) VALUES
  ('mare',    'A', NULL, 2, 'are', 'm', NULL, 'mare'),
  ('frumos',  'A', NULL, 2, 'mos', 'f', NULL, 'frumoasă'),
  ('minunat', 'A', NULL, 3, 'nat', 'm', NULL, 'minunată'),
  ('blând',   'A', NULL, 1, 'ând', 'b', NULL, 'blândă'),
  ('miraculos','A', NULL, 4, 'los', 'm', NULL, 'miraculoasă');

-- Verbs (type='V')
INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine) VALUES
  ('marchează','V', NULL, 3, 'ază', 'm', NULL, NULL),
  ('merge',    'V', NULL, 2, 'rge', 'm', NULL, NULL),
  ('aleargă',  'V', NULL, 3, 'rgă', 'a', NULL, NULL),
  ('mănâncă',  'V', NULL, 3, 'ncă', 'm', NULL, NULL),
  ('colindă',  'V', NULL, 3, 'ndă', 'c', NULL, NULL),
  ('bate',     'V', NULL, 2, 'ate', 'b', NULL, NULL);
