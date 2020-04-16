package ie.gmit.sw.database;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import ie.gmit.sw.ai.cloud.WordFrequency;
import ie.gmit.sw.database.interfaces.Database;

/**
 * @author Kevin Niland
 * @category Database
 * @version 1.0
 *
 *          RealWordDatabase
 * 
 *          Implements Database - Acts as the real/concrete implementation of
 *          Database
 * 
 *          Generates a database of words from which the word cloud is generated
 */
public class RealDatabase implements Database {
	private static RealDatabase realDatabase;
	private WordFrequency[] wordFrequencies = new WordFrequency[32];
	private ConcurrentHashMap<String, Integer> wordMap = new ConcurrentHashMap<String, Integer>();
	private List<WordFrequency> popularWords = new ArrayList<WordFrequency>();
	private Set<String> ignoreList = new ConcurrentSkipListSet<String>();
	private BufferedReader bufferedReader = null;
	private String line, ignoreWord;
	private int i, counter = 0;

	// Singleton design pattern - Double-checked locking principle
	private RealDatabase() {

	}

	/**
	 * In this approach, the synchronized block is used inside the if condition with
	 * an additional check to ensure that only one instance of a singleton class is
	 * created
	 */
	public static RealDatabase getInstance() {
		if (realDatabase == null) {
			synchronized (RealDatabase.class) {
				if (realDatabase == null) {
					realDatabase = new RealDatabase();
				}
			}
		}

		return realDatabase;
	}

	/**
	 * Adds a word to the wordMap
	 * 
	 * @param word - Word to be added to map
	 */
	@Override
	public void addWord(String word) {
		ignoreWord = word.substring(0, 1).toUpperCase().concat(word.substring(1));

		/**
		 * If the list containing the ignore words doesn't contain 'word' and wordMap
		 * contains ...
		 */
		if (!ignoreList.contains(ignoreWord) && wordMap.containsKey(ignoreWord)) {
			counter = wordMap.get(ignoreWord);
			counter++;

			wordMap.put(ignoreWord, counter);
		} else {
			wordMap.put(word, 1);

			return;
		}
	}

	@Override
	public WordFrequency[] getWordFrequency() {
		this.wordMap.entrySet().forEach(entry -> {
			popularWords.add(new WordFrequency(entry.getKey(), entry.getValue()));
		});

		// Sort the list of popular words
		Collections.sort(popularWords);

		for (i = 0; i < 32; i++) {
			wordFrequencies[i] = popularWords.get(i);
		}

		return wordFrequencies;
	}

	/**
	 * Clear words from the wordMap and popular words list. Subsequent searches
	 * would return words from previous searches
	 */
	@Override
	public void clear() {
		wordMap.clear();
		popularWords.clear();
	}

	/**
	 * Ignores a words from file
	 * 
	 * @param fileIgnore - File to ignore words/search terms from
	 * @throws IOException
	 */
	@Override
	public void ignoreFromFile(File fileIgnore) throws IOException {
		try {
			bufferedReader = new BufferedReader(new FileReader(fileIgnore));

			while ((line = bufferedReader.readLine()) != null) {
				ignoreList.add(line.substring(0, 1).toUpperCase().concat(line.substring(1)));
			}
		} catch (FileNotFoundException fileNotFoundException) {
			fileNotFoundException.printStackTrace();
		}
	}

	/**
	 * Ignores a word from a search
	 * 
	 * @param wordIgnore - Word/search term from search to ignore
	 */
	@Override
	public void ignoreFromSearch(String wordIgnore) {
		ignoreList.add(wordIgnore.substring(0, 1).toUpperCase().concat(wordIgnore.substring(1)));
	}

	/**
	 * Return the list containing all ignored words
	 * 
	 * @return ignoreList - List containing all the words/search terms to ignore
	 */
	@Override
	public Set<String> ignore() {
		return ignoreList;
	}
}
