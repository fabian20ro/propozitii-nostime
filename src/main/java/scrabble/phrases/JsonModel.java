package scrabble.phrases;

/**
 * Response model for JSON API containing generated sentences.
 *
 * @param haiku the generated haiku
 * @param fiveWordSentence the generated five-word sentence
 */
public record JsonModel(String haiku, String fiveWordSentence) {}
