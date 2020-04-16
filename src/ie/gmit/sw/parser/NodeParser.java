package ie.gmit.sw.parser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.encog.engine.network.activation.ActivationSigmoid;
import org.encog.ml.data.MLData;
import org.encog.ml.data.MLDataPair;
import org.encog.ml.data.MLDataSet;
import org.encog.ml.data.basic.BasicMLDataSet;
import org.encog.neural.networks.BasicNetwork;
import org.encog.neural.networks.layers.BasicLayer;
import org.encog.neural.networks.training.propagation.resilient.ResilientPropagation;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import ie.gmit.sw.database.RealDatabase;
import ie.gmit.sw.parser.interfaces.Parser;
import net.sourceforge.jFuzzyLogic.FIS;
import net.sourceforge.jFuzzyLogic.FunctionBlock;
import net.sourceforge.jFuzzyLogic.rule.Variable;

/**
 * @author Kevin Niland
 * @category Parser
 * @version 1.0
 *
 *          NodeParser - Implementation of Parser
 * 
 *          Searches Duck Duck Go for the inputted search term, adds the results
 *          to WordDatabase, performs processing, and calculates a fuzzy value
 *          and encog value
 */
public class NodeParser implements Parser, Runnable {
	private RealDatabase wordDatabase = RealDatabase.getInstance();
	private Random random = new Random();

	private List<Document> urlList = new ArrayList<Document>();
	private List<String> childList = new ArrayList<String>();
	private List<String> searchList = new ArrayList<String>();
	private List<String> processedList = new ArrayList<String>();

	private File jfuzzyFile;
	private String url, absURL;
	private static int MAX = 25;
	private int lowScore = 0, averageScore = 0, highScore = 0, score = 7, epoch = 1;
	private double encogLow = 0, encogAvg = 0, encogHigh = 0, minError = 0.07, correct = 0, total = 0, fuzzyValue = 0,
			accuracy = 0;

	public NodeParser() {

	}

	/**
	 * @param file       - jFuzzy file
	 * @param url        - URL being searched
	 * @param searchTerm - Term searched for by Duck Duck Go
	 */
	public NodeParser(File file, String url, String searchTerm) {
		this.jfuzzyFile = file;
		this.url = url;
		this.searchList = processTerms(searchTerm);
	}

	@Override
	public void run() {
		System.out.println("Searching...");

		try {
			// Connect to Duck Duck Go
			Document document = Jsoup.connect(this.url).get();

			urlList.add(document);

			// Searches Duck Duck Go for the search term
			searchDuckDuckGo();
		} catch (Exception exception) {
			exception.printStackTrace();
		}
	}

	/**
	 * Searches Duck Duck Go for the search term specified
	 * 
	 * @throws IOException
	 */
	@Override
	public void searchDuckDuckGo() throws IOException {
		/**
		 * While the size of closedList is less than MAX and the openList isn't empty,
		 * search Duck Duck Go for the term entered by the user
		 */
		while (childList.size() <= MAX && !urlList.isEmpty()) {
			/**
			 * Remove an item from openList at a random index and select an element that is
			 * a link
			 */
			Document document = urlList.remove(random.nextInt(urlList.size()));
			Elements elements = document.select("a[href]");

			// Pass the body text to addWord() to be added to the wordDatabase
			addWord(document.body().text());

			// For each element in elements, get an absolute URL from a URL attribute (href)
			for (Element element : elements) {
				absURL = element.absUrl("href");

				// Make absURL isn't null
				assert absURL != null;

				/**
				 * If the absolute URL isn't null and closedList doesn't contain the absolute
				 * URL and closedList size is less than or equal to MAX, check if each
				 * searchTerm in searchList contains the searchTerm. If true, try and create a
				 * new connection to the absolute URL
				 */
				if (!childList.contains(absURL) && childList.size() <= MAX) {
					for (String searchTerm : searchList) {
						if (absURL.contains(searchTerm)) {
							addURL(absURL);
						}
					}
				}
			}
		}
	}

	/**
	 * Gets child element from absolute URL and adds the URL and child to two
	 * separate lists
	 * 
	 * @param absURL - Absolute URL from a URL attribute
	 * @throws IOException
	 */
	public void addURL(String absURL) throws IOException {
		Document child = Jsoup.connect(absURL).get();

		/**
		 * If the fuzzy heuristic of child is greater than or equal to score, add the
		 * body text of the child to addWord(), add the absURL to closedList and add
		 * child to openList
		 */
		if (calculateFuzzyValue(child) >= score) {
			addWord(child.body().text());

			childList.add(absURL);
			urlList.add(child);
		}
	}

	/**
	 * Adds text to the wordDatabase
	 * 
	 * @param text - Text from body of document or child
	 */
	@Override
	public void addWord(String text) {
		/**
		 * "\\W+" matches all characters except alphanumeric characters and _, however,
		 * numbers were still being added to the word cloud and as such, I added a list
		 * of numbers to the ignore file which seems to resolve this issue
		 */
		String[] words = text.split("\\W+");

		/**
		 * For each word in the words array, add the word to wordDatabase and remove any
		 * whitespace
		 */
		for (String word : words) {
			wordDatabase.addWord(word.trim());
		}
	}

	/**
	 * Processes a search term
	 * 
	 * @param searchTerm - Search term to be processed
	 * @return processedList - Return the list of processed terms
	 */
	@Override
	public List<String> processTerms(String searchTerm) {
		String[] words = searchTerm.split("\\W+");

		/**
		 * For each word in the words array, check if the ignore list from the word
		 * database doesn't contain word
		 */
		for (String word : words) {
			if (!wordDatabase.ignore().contains(word)) {
				processedList.add(word.trim());

				wordDatabase.ignoreFromSearch(word.trim());
			}
		}

		// Return processedList
		return processedList;
	}

	/**
	 * Adapted from jFuzzyLogic documentation:
	 * http://jfuzzylogic.sourceforge.net/html/manual.html
	 * 
	 * Calculates the fuzzy value of the total 'score' of the title, headings, and
	 * body of a web page with the search term
	 * 
	 * @param document - Child element of a URL
	 * 
	 * @return variable.getLatestDefuzzifiedValue() - Defuzzified value of variable
	 */
	@Override
	public double calculateFuzzyValue(Document document) {
		// Load the FCL file and create a Fuzzy Inference System (FIS)
		FIS fis = FIS.load(jfuzzyFile.getAbsolutePath(), true);
		Elements heading = document.select("h1, h2, h3, h4");
		Elements body = document.select("p");

		// Error while loading?
		if (fis == null) {
			System.err.println("ERROR: Can't load file: '" + jfuzzyFile + "'");

			return 0.0;
		}

		/**
		 * For each searchTerm searchList, check if the title element contains the
		 * searchTerm. Increment highScore if true
		 */
		for (String searchTerm : searchList) {
			if (document.title().toLowerCase().contains(searchTerm)) {
				highScore++;
			}
		}

		// Set input for title
		fis.setVariable("title", highScore);

		/**
		 * For each heading element in headings and for each searchWord in searchList,
		 * check if the heading element contains the searchTerm. Increment averageScore
		 * if true
		 */
		for (Element h : heading) {
			for (String searchTerm : searchList) {
				if (h.toString().contains(searchTerm)) {
					averageScore++;
				}
			}
		}

		// Set input for headings
		fis.setVariable("headings", averageScore);

		/**
		 * For each paragraph element in paragraphs and for each searchTerm in
		 * searchList, check if the paragraph element contains the searchTerm. Increment
		 * lowScore if true
		 */
		for (Element b : body) {
			for (String searchTerm : searchList) {
				if (b.toString().contains(searchTerm)) {
					lowScore++;
				}
			}
		}

		// Set input for body
		fis.setVariable("body", lowScore);

		FunctionBlock functionBlock = fis.getFunctionBlock("wcloud");

		// Show
//		JFuzzyChart.get().chart(functionBlock);

		// Evaluate
		fis.evaluate();

		// Show output variable's chart
		Variable variable = functionBlock.getVariable("score");
//		JFuzzyChart.get().chart(variable, variable.getDefuzzifier(), true);

		fuzzyValue = variable.getLatestDefuzzifiedValue();

		// Return the defuzzified value of variable
		return fuzzyValue;
	}

	/**
	 * @return fuzzyValue - Print calculated fuzzy value
	 */
	public double getFuzzyValue() {
		System.out.println("Fuzzy value: " + fuzzyValue);

		return fuzzyValue;
	}

	/**
	 * Set the fuzzy value
	 * 
	 * @param fuzzyValue
	 */
	public void setFuzzyValue(double fuzzyValue) {
		this.fuzzyValue = fuzzyValue;
	}

	/**
	 * @param document - Child element of a URL
	 * 
	 * @return accuracy - Accuracy of
	 */
	@Override
	public double calculateEncogValue(Document document) {
		Elements heading = document.select("h1, h2, h3, h4");
		Elements body = document.select("p");

		/**
		 * For each searchTerm searchList, check if the title element contains the
		 * searchTerm. Increment highScore if true
		 */
		for (String searchTerm : searchList) {
			if (document.title().toLowerCase().contains(searchTerm)) {
				highScore++;
			}
		}

		/**
		 * For each heading element in headings and for each searchWord in searchList,
		 * check if the heading element contains the searchTerm. Increment averageScore
		 * if true
		 */
		for (Element h : heading) {
			for (String searchTerm : searchList) {
				if (h.toString().contains(searchTerm)) {
					averageScore++;
				}
			}
		}

		/**
		 * For each paragraph element in paragraphs and for each searchTerm in
		 * searchList, check if the paragraph element contains the searchTerm. Increment
		 * lowScore if true
		 */
		for (Element b : body) {
			for (String searchTerm : searchList) {
				if (b.toString().contains(searchTerm)) {
					lowScore++;
				}
			}
		}

		encogLow = lowScore;
		encogAvg = averageScore;
		encogHigh = highScore;

		// Step 1: Declare a network topology
		BasicNetwork basicNetwork = new BasicNetwork();

		basicNetwork.addLayer(new BasicLayer(null, true, 16));
		basicNetwork.addLayer(new BasicLayer(new ActivationSigmoid(), true, 2));
		basicNetwork.addLayer(new BasicLayer(new ActivationSigmoid(), false, 7));

		basicNetwork.getStructure().finalizeStructure();
		basicNetwork.reset();

		// Step 2: Create the training data set
		MLDataSet mlDataSet = new BasicMLDataSet(data, expected);

		// Step 3: Train the neural network
		ResilientPropagation resilientPropagation = new ResilientPropagation(basicNetwork, mlDataSet);

		System.out.println("Training...");

		do {
			resilientPropagation.iteration();
			epoch++;
		} while (resilientPropagation.getError() > minError);

		resilientPropagation.finishTraining();

		System.out.println("Training complete!");

		// Step 4: Test the neural network
		for (MLDataPair pair : mlDataSet) {
			total++;

			MLData output = basicNetwork.compute(pair.getInput());

			int y = (int) Math.round(output.getData(0));
			int yd = (int) pair.getIdeal().getData(0);

			if (y == yd) {
				correct++;
			}
		}

		System.out.println("Testing complete! Accuracy: ");

		accuracy = (correct / total) * 100;

		return accuracy;
	}

	/**
	 * @return accuracy - Print the calculated accuracy
	 */
	public double getAccuracy() {
		System.out.println("Fuzzy value: " + accuracy);

		return accuracy;
	}

	/**
	 * Set the accuracy
	 * 
	 * @param accuracy
	 */
	public void setAccuracy(double accuracy) {
		this.accuracy = accuracy;
	}

	private double[][] data = { { 1, 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 0.5, 0, 0, 1 },
			{ 1, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0.5, 1, 0, 1 }, { 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 1, 0, 1, 0, 0 },
			{ 1, 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 0.5, 0, 0, 1 }, { 1, 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 0.5, 1, 0, 1 },
			{ 1, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0.5, 1, 0, 1 }, { 1, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0.5, 1, 1, 1 },
			{ 0, 0, 1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 0, 1, 1, 0 }, { 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 1, 0, 1, 0, 0 },
			{ 1, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0.5, 0, 1, 0 }, { 1, 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 0.5, 1, 0, 1 },
			{ 0, 1, 1, 0, 1, 0, 0, 0, 1, 1, 0, 0, 0.25, 1, 1, 0 }, { 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 1, 0, 1, 0, 0 },
			{ 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, { 0, 0, 1, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0.5, 0, 0, 0 },
			{ 0, 0, 1, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0.75, 0, 0, 0 },
			{ 0, 1, 1, 0, 1, 0, 1, 0, 1, 1, 0, 0, 0.25, 1, 0, 0 }, { 1, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0.5, 1, 0, 1 },
			{ 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 1, 0, 1, 0, 1 }, { 0, 0, 0, 1, 0, 1, 1, 1, 1, 1, 0, 1, 0, 1, 0, 1 },
			{ 0, 1, 1, 0, 1, 0, 0, 0, 1, 1, 0, 0, 0.25, 1, 1, 0 },
			{ 0, 1, 1, 0, 1, 1, 0, 0, 1, 1, 0, 0, 0.25, 1, 0, 0 }, { 1, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0.5, 1, 0, 1 },
			{ 0, 1, 1, 0, 1, 0, 0, 0, 1, 1, 0, 0, 0.25, 1, 0, 1 },
			{ 0, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0.75, 0, 0, 0 }, { 0, 0, 1, 0, 0, 1, 1, 1, 1, 1, 0, 0, 0.5, 0, 0, 0 },
			{ 0, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0.5, 0, 0, 0 }, { 1, 0, 0, 1, 1, 0, 0, 1, 1, 1, 0, 0, 0.25, 1, 0, 0 },
			{ 1, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0.5, 1, 0, 1 }, { 1, 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 0.25, 0, 1, 1 },
			{ 0, 0, 1, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0.75, 0, 0, 0 }, { 1, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0.5, 1, 1, 1 },
			{ 1, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0.25, 0, 0, 1 },
			{ 0, 1, 1, 0, 1, 1, 1, 0, 1, 1, 0, 0, 0.25, 1, 0, 0 }, { 0, 0, 1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0 },
			{ 1, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0.5, 1, 1, 0 }, { 1, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0.5, 1, 0, 0 },
			{ 0, 1, 1, 0, 1, 0, 1, 0, 1, 1, 0, 0, 0.25, 1, 0, 0 }, { 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 1, 0, 1, 0, 0 },
			{ 1, 0, 1, 0, 1, 0, 0, 0, 0, 1, 1, 0, 0.75, 0, 1, 0 },
			{ 1, 0, 1, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0.75, 0, 0, 0 },
			{ 0, 1, 1, 0, 0, 0, 1, 0, 1, 1, 0, 0, 0.25, 1, 0, 0 },
			{ 0, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 0.75, 0, 0, 0 },
			{ 0, 1, 1, 0, 1, 0, 0, 0, 1, 1, 0, 0, 0.25, 1, 0, 0 }, { 1, 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 0.5, 1, 0, 1 },
			{ 1, 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 0.5, 1, 0, 1 }, { 0, 0, 1, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0.75, 0, 0, 0 },
			{ 1, 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 0.5, 1, 0, 1 }, { 1, 0, 0, 1, 0, 1, 1, 1, 1, 1, 0, 0, 0.5, 1, 0, 1 },
			{ 1, 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 0.5, 1, 0, 0 }, { 1, 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 0.5, 1, 0, 1 },
			{ 1, 0, 1, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0.75, 0, 0, 0 }, { 0, 0, 1, 0, 0, 1, 1, 1, 1, 1, 0, 0, 0.5, 1, 0, 0 },
			{ 0, 0, 1, 0, 0, 1, 1, 0, 0, 0, 0, 0, 1, 0, 0, 1 }, { 1, 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 0.5, 1, 0, 0 },
			{ 1, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0.5, 1, 0, 1 }, { 0, 1, 1, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0.25, 1, 0, 1 },
			{ 0, 1, 1, 0, 1, 0, 0, 0, 1, 1, 0, 0, 0.25, 1, 1, 0 },
			{ 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 0, 0, 0.25, 1, 0, 1 },
			{ 0, 1, 1, 0, 1, 0, 0, 0, 1, 1, 0, 0, 0.25, 1, 0, 0 }, { 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 1, 0, 1, 0, 1 },
			{ 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 1, 0, 1, 0, 0 }, { 0, 0, 1, 0, 0, 0, 1, 1, 1, 1, 1, 0, 0, 1, 0, 0 },
			{ 1, 0, 1, 1, 0, 1, 1, 0, 1, 1, 0, 0, 0.5, 1, 0, 1 }, { 1, 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 0.5, 1, 0, 1 },
			{ 1, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0.5, 1, 1, 1 }, { 0, 0, 0, 1, 0, 1, 1, 1, 1, 1, 0, 1, 0, 1, 0, 1 },
			{ 1, 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 0.5, 1, 0, 1 }, { 1, 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 0.5, 1, 1, 1 },
			{ 1, 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 0.5, 1, 0, 1 }, { 1, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0.5, 1, 1, 1 },
			{ 0, 1, 1, 0, 0, 0, 1, 0, 1, 1, 0, 0, 0.25, 1, 0, 1 }, { 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 1, 1, 0, 0 },
			{ 0, 0, 1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0 }, { 1, 0, 0, 1, 0, 1, 1, 1, 1, 1, 0, 1, 0, 0, 0, 1 },
			{ 1, 0, 0, 1, 0, 1, 1, 1, 1, 1, 0, 1, 0.25, 1, 0, 1 }, { 0, 0, 0, 0, 0, 1, 1, 1, 1, 0, 1, 0, 0, 1, 0, 0 },
			{ 0, 0, 1, 0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0 }, { 0, 1, 1, 0, 1, 1, 1, 0, 1, 1, 0, 0, 0.25, 1, 0, 0 },
			{ 0, 1, 1, 0, 1, 1, 1, 0, 1, 1, 0, 0, 0.25, 1, 0, 0 }, { 0, 0, 1, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 1, 0, 0 },
			{ 0, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0 }, { 0, 0, 1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0 },
			{ 0, 1, 1, 0, 1, 0, 0, 0, 1, 1, 0, 0, 0.25, 1, 0, 0 },
			{ 1, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0.25, 1, 0, 0 },
			{ 0, 0, 1, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0.625, 0, 0, 0 }, { 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 1, 1, 0, 1, 0, 1 },
			{ 0, 1, 1, 0, 1, 1, 0, 0, 1, 1, 0, 0, 0.25, 1, 0, 1 },
			{ 0, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0.75, 0, 0, 0 }, { 0, 0, 1, 0, 0, 1, 0, 1, 1, 1, 0, 0, 0.5, 0, 0, 0 },
			{ 0, 0, 1, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0.5, 1, 0, 1 }, { 0, 0, 1, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0.5, 1, 0, 0 },
			{ 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 1, 0, 1, 0, 1 }, { 1, 0, 0, 1, 1, 0, 0, 1, 1, 1, 0, 0, 0.25, 1, 0, 0 },
			{ 1, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0.5, 1, 0, 0 }, { 0, 1, 1, 0, 1, 0, 1, 0, 1, 1, 0, 0, 0.25, 1, 0, 1 },
			{ 1, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0.25, 1, 0, 1 },
			{ 1, 0, 1, 0, 1, 0, 0, 0, 0, 1, 1, 0, 0.75, 0, 0, 0 }, { 1, 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 0.5, 1, 0, 1 },
			{ 0, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0 }, { 0, 1, 1, 0, 1, 0, 0, 0, 1, 1, 0, 0, 0.25, 1, 0, 0 } };

	private double[][] expected = { { 1, 0, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 }, { 0, 0, 0, 1, 0, 0, 0 },
			{ 1, 0, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 },
			{ 0, 0, 0, 1, 0, 0, 0 }, { 0, 0, 0, 1, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 },
			{ 0, 1, 0, 0, 0, 0, 0 }, { 0, 0, 0, 1, 0, 0, 0 }, { 0, 0, 0, 0, 0, 0, 1 }, { 0, 0, 0, 0, 0, 0, 1 },
			{ 0, 0, 0, 0, 0, 0, 1 }, { 0, 1, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 }, { 0, 0, 0, 1, 0, 0, 0 },
			{ 1, 0, 0, 0, 0, 0, 0 }, { 0, 1, 0, 0, 0, 0, 0 }, { 0, 1, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 },
			{ 0, 1, 0, 0, 0, 0, 0 }, { 0, 0, 0, 0, 0, 1, 0 }, { 0, 0, 0, 0, 1, 0, 0 }, { 0, 0, 0, 0, 1, 0, 0 },
			{ 1, 0, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 }, { 0, 0, 0, 0, 0, 1, 0 },
			{ 1, 0, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 }, { 0, 1, 0, 0, 0, 0, 0 }, { 0, 0, 0, 1, 0, 0, 0 },
			{ 1, 0, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 }, { 0, 1, 0, 0, 0, 0, 0 }, { 0, 0, 0, 1, 0, 0, 0 },
			{ 0, 0, 0, 0, 0, 1, 0 }, { 0, 0, 0, 0, 0, 1, 0 }, { 0, 1, 0, 0, 0, 0, 0 }, { 0, 0, 0, 0, 0, 1, 0 },
			{ 0, 1, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 }, { 0, 0, 0, 0, 0, 0, 1 },
			{ 1, 0, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 },
			{ 0, 0, 0, 0, 0, 1, 0 }, { 0, 0, 0, 0, 1, 0, 0 }, { 0, 0, 0, 0, 0, 0, 1 }, { 1, 0, 0, 0, 0, 0, 0 },
			{ 1, 0, 0, 0, 0, 0, 0 }, { 0, 1, 0, 0, 0, 0, 0 }, { 0, 1, 0, 0, 0, 0, 0 }, { 0, 1, 0, 0, 0, 0, 0 },
			{ 0, 1, 0, 0, 0, 0, 0 }, { 0, 0, 0, 1, 0, 0, 0 }, { 0, 0, 0, 1, 0, 0, 0 }, { 0, 0, 1, 0, 0, 0, 0 },
			{ 1, 0, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 },
			{ 1, 0, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 },
			{ 0, 1, 0, 0, 0, 0, 0 }, { 0, 0, 0, 0, 0, 0, 1 }, { 0, 0, 0, 1, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 },
			{ 1, 0, 0, 0, 0, 0, 0 }, { 0, 0, 1, 0, 0, 0, 0 }, { 0, 0, 0, 0, 0, 0, 1 }, { 0, 1, 0, 0, 0, 0, 0 },
			{ 0, 1, 0, 0, 0, 0, 0 }, { 0, 0, 1, 0, 0, 0, 0 }, { 0, 0, 0, 0, 0, 0, 1 }, { 0, 0, 0, 1, 0, 0, 0 },
			{ 0, 1, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 }, { 0, 0, 0, 0, 0, 0, 1 }, { 0, 0, 0, 1, 0, 0, 0 },
			{ 0, 1, 0, 0, 0, 0, 0 }, { 0, 0, 0, 0, 0, 1, 0 }, { 0, 0, 0, 0, 1, 0, 0 }, { 0, 0, 1, 0, 0, 0, 0 },
			{ 0, 0, 1, 0, 0, 0, 0 }, { 0, 0, 0, 1, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 },
			{ 0, 1, 0, 0, 0, 0, 0 }, { 1, 0, 0, 0, 0, 0, 0 }, { 0, 0, 0, 0, 0, 1, 0 }, { 1, 0, 0, 0, 0, 0, 0 },
			{ 0, 0, 0, 0, 0, 0, 1 }, { 0, 1, 0, 0, 0, 0, 0 } };
}