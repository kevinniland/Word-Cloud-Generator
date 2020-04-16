package ie.gmit.sw;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ie.gmit.sw.ai.cloud.LogarithmicSpiralPlacer;
import ie.gmit.sw.ai.cloud.WeightedFont;
import ie.gmit.sw.ai.cloud.WordFrequency;
import ie.gmit.sw.database.RealDatabase;
import ie.gmit.sw.parser.NodeParser;

/**
 * @author John Healy, Kevin Niland
 * @category Web Searcher
 * @version 1.0
 */
public class ServiceHandler extends HttpServlet {
	private RealDatabase realDatabase = RealDatabase.getInstance();
	private BufferedImage bufferedImage = null;
	private ExecutorService executorService = Executors.newFixedThreadPool(20);
	private File ignoreWordsFile, jfuzzyFile;
	private static final long serialVersionUID = 1L;
	private String browser, chosenBrowser, option, query;

	/**
	 * Gets a handle on the application context, reads values from context-param,
	 * and passes ignoreWords file to the database
	 */
	public void init() throws ServletException {
		ServletContext servletContext = getServletContext(); // Get a handle on the application context

		/**
		 * USEFUL - If all else fails and the application can't find either files, place
		 * them in the directory shown here and update the web.xml accordingly
		 */
//		System.out.println(System.getProperty("user.dir"));

		// Reads the value from the <context-param> in web.xml
		ignoreWordsFile = new File(getServletContext().getRealPath(File.separator),
				servletContext.getInitParameter("IGNORE_WORDS_FILE"));

		// Reads the value from the <context-param> in web.xml
		jfuzzyFile = new File(getServletContext().getRealPath(File.separator),
				servletContext.getInitParameter("JFUZZY_FILE"));

		// Pass the ignoreWords file to the word database
		try {
			realDatabase.ignoreFromFile(ignoreWordsFile);
		} catch (IOException ioException) {
			ioException.printStackTrace();
		}
	}

	/**
	 * Displays the results of a search in a word cloud
	 */
	public void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
			throws ServletException, IOException {
		httpServletResponse.setContentType("text/html"); // Output the MIME type

		// Write out text. We can write out binary too and change the MIME type
		PrintWriter printWriter = httpServletResponse.getWriter();

		/**
		 * Initialize some request variables with the submitted form info. These are
		 * local to this method and thread safe
		 */
		option = httpServletRequest.getParameter("cmbOptions");
		query = httpServletRequest.getParameter("query");

		printWriter.print("<html><head><title>Artificial Intelligence Assignment</title>");
		printWriter.print("<link rel=\"stylesheet\" href=\"includes/style.css\">");
		printWriter.print("</head>");
		printWriter.print("<body>");
		printWriter.print(
				"<div style=\"font-size:48pt; font-family:arial; color:#990000; font-weight:bold\">Web Opinion Visualiser</div>");
		printWriter.print("<p><h2>Please read the following carefully</h2>");
		printWriter.print("<p>The &quot;ignore words&quot; file is located at <font color=red><b>"
				+ ignoreWordsFile.getAbsolutePath() + "</b></font> and is <b><u>" + ignoreWordsFile.length()
				+ "</u></b> bytes in size.");

		// Displays chosen browser
		switch (option) {
		case "Option 1":
			chosenBrowser = "Browser: Google (A fine choice)";
			break;
		case "Option 2":
			chosenBrowser = "Browser: Duck Duck Go (A respectable choice)";
			break;
		case "Option 3":
			chosenBrowser = "Browser: Bing (Why? Are you alright?)";
			break;
		}

		printWriter.print("<p><b>Chosen browser: " + chosenBrowser + "</b></p>");
		printWriter.print(
				"<p>The &quot;ignore words&quot; file is located at <font color=red><b>" + jfuzzyFile.getAbsolutePath()
						+ "</b></font> and is <b><u>" + jfuzzyFile.length() + "</u></b> bytes in size.");
		printWriter.print(
				"You must place any additional files in the <b>res</b> directory and access them in the same way as the set of ignore words.");
		printWriter.print(
				"<p>Place any additional JAR archives in the WEB-INF/lib directory. This will result in Tomcat adding the library of classes ");
		printWriter.print(
				"to the CLASSPATH for the web application context. Please note that the JAR archives <b>jFuzzyLogic.jar</b>, <b>encog-core-3.4.jar</b> and ");
		printWriter.print("<b>jsoup-1.12.1.jar</b> have already been added to the project.");
		printWriter.print("<p><fieldset><legend><h3>Result</h3></legend>");

		// Make sure query isn't null - would this be the correct way of going about it??
		assert query != null;

		try {
			Go(option, query);

			executorService.awaitTermination(20, TimeUnit.SECONDS);

			System.out.println("Done - Finished searching");
		} catch (InterruptedException interruptedException) {
			interruptedException.printStackTrace();
		}

		// Get fuzzy value and accuracy
		new NodeParser().getFuzzyValue();
		new NodeParser().getAccuracy();

		WordFrequency[] words = new WeightedFont().getFontSizes(realDatabase.getWordFrequency());
		Arrays.sort(words, Comparator.comparing(WordFrequency::getFrequency, Comparator.reverseOrder()));

		// Spira Mirabilis
		LogarithmicSpiralPlacer logarithmicSpiralPlacer = new LogarithmicSpiralPlacer(800, 600);

		for (WordFrequency word : words) {
			// Place each word on the canvas starting with the largest
			logarithmicSpiralPlacer.place(word);
		}

		// Get a handle on the word cloud graphic
		BufferedImage cloud = logarithmicSpiralPlacer.getImage();

		printWriter.print("<img src=\"data:image/png;base64," + encodeToString(cloud) + "\" alt=\"Word Cloud\">");
		printWriter.print("</fieldset>");
		printWriter.print(
				"<P>Maybe output some search stats here, e.g. max search depth, effective branching factor.....<p>");
		printWriter.print("<a href=\"./\">Return to Start Page</a>");
		printWriter.print("</body>");
		printWriter.print("</html>");

		// Clear the database of words for another search
		realDatabase.clear();
	}

	/**
	 * Searches for the inputed search term
	 * 
	 * @param option     - Chosen option (determines browser)
	 * @param searchTerm - Searches for the entered search term
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void Go(String option, String searchTerm) throws IOException, InterruptedException {
		// Determines what browser the application will use to search for the term
		switch (option) {
		case "Option 1":
			browser = "https://www.google.com/search?q=";
			break;
		case "Option 2":
			browser = "https://duckduckgo.com/html/?q=";
			break;
		case "Option 3":
			browser = "https://www.bing.com/search?q=";
			break;
		}

		// Connect to Duck Duck Go and search for the entered search term
		Document document = Jsoup.connect(browser + searchTerm).get();
		Elements elements = document.getElementById("links").getElementsByClass("results_links");

		System.out.println("Adding word to database...");
		System.out.println("Ignoring words from file...");
		System.out.println("Ignoring words from search...");
		System.out.println("Getting ignore list...");
		System.out.println("Getting word frequencies...");

		for (Element element : elements) {
			Element title = element.getElementsByClass("links_main").first().getElementsByTag("a").first();

			// Threaded aspect
			executorService.execute(new NodeParser(jfuzzyFile, title.attr("href"), searchTerm));
		}
	}

	public void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
			throws ServletException, IOException {
		doGet(httpServletRequest, httpServletResponse);
	}

	/**
	 * Encodes an image to string
	 * 
	 * @param bufferedImage
	 * @return encodedString
	 */
	private String encodeToString(BufferedImage bufferedImage) {
		String encodedString = null;
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

		try {
			ImageIO.write(bufferedImage, "png", byteArrayOutputStream);
			byte[] bytes = byteArrayOutputStream.toByteArray();

			Base64.Encoder encoder = Base64.getEncoder();
			encodedString = encoder.encodeToString(bytes);

			byteArrayOutputStream.close();
		} catch (IOException ioException) {
			ioException.printStackTrace();
		}

		return encodedString;
	}

	/**
	 * Decodes a string to an image
	 * 
	 * @param imageString - Image to be decoded to string
	 * @return bufferedImage
	 */
	private BufferedImage decodeToImage(String imageString) {
		byte[] bytes;

		try {
			Base64.Decoder decoder = Base64.getDecoder();
			bytes = decoder.decode(imageString);

			ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
			bufferedImage = ImageIO.read(byteArrayInputStream);

			byteArrayInputStream.close();
		} catch (Exception exception) {
			exception.printStackTrace();
		}

		return bufferedImage;
	}
}