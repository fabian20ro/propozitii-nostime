package scrabble.phrases;

/**
 * Response record for a single generated sentence.
 *
 * @param sentence the generated sentence with HTML formatting
 */
public record SentenceResponse(String sentence) {}
