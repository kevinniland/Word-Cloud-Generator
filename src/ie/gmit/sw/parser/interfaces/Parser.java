package ie.gmit.sw.parser.interfaces;

import java.io.IOException;
import java.util.List;

import org.jsoup.nodes.Document;

/**
 * @author Kevin Niland
 * @category Parser
 * @version 1.0
 * 
 *          NodeParserInterface
 * 
 *          Interface for NodeParser
 */
public interface Parser {
	public void searchDuckDuckGo() throws IOException;

	public void addWord(String text);

	public List<String> processTerms(String searchTerm);

	public double calculateFuzzyValue(Document document);

	public double calculateEncogValue(Document document);
}
