package ie.gmit.sw.ai.cloud;

/**
 * @author John Healy
 * @category @version 1.0
 * 
 *           WordFrequency
 * 
 *           Determines frequency of each word
 */
public class WordFrequency implements Comparable<WordFrequency> {
	private String word;
	private int frequency;
	private int fontSize = 0;

	public WordFrequency(String word, int frequency) {
		this.word = word;
		this.frequency = frequency;
	}

	public String getWord() {
		return this.word;
	}

	public void setWord(String word) {
		this.word = word;
	}

	public int getFrequency() {
		return this.frequency;
	}

	public void setFrequency(int frequency) {
		this.frequency = frequency;
	}

	public int getFontSize() {
		return this.fontSize;
	}

	public void setFontSize(int size) {
		this.fontSize = size;
	}

	public String toString() {
		return "Word: " + getWord() + "\tFreq: " + getFrequency() + "\tFont Size: " + getFontSize();
	}

	@Override
	public int compareTo(WordFrequency compare) {
		// TODO Auto-generated method stub
		return -Integer.compare(frequency, compare.getFrequency());
	}
}