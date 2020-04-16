package ie.gmit.sw.database.interfaces;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import ie.gmit.sw.ai.cloud.WordFrequency;

/**
 * @author Kevin Niland
 * @category Database
 * @version 1.0
 *
 *          Database
 * 
 *          Interface for RealDatabase
 */
public interface Database {
	abstract public void addWord(String word);

	abstract public WordFrequency[] getWordFrequency();

	abstract public void clear();

	abstract public void ignoreFromFile(File fileIgnore) throws IOException;

	abstract public void ignoreFromSearch(String wordIgnore);

	abstract public Set<String> ignore();
}
