import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.util.FileManager;

public class StartLinkchecker {
	static Linkchecker lc = new Linkchecker();
	static Logger logger = Logger.getLogger(StartLinkchecker.class.getName());

	static long now;
	static long after;

	/**
	 * Reads n-triple files and test all object URIs which are predicated by
	 * lv:fulltextOnline
	 * 
	 * @param args
	 *            the first parameter is the name of the file
	 * @throws IOException
	 * @throws NoSuchElementException
	 */
	public static void main(String[] args) throws NoSuchElementException, IOException {

		System.out.println("We are here: " + (new File(".")).getAbsolutePath());
		// File f = new File(args[0]);
		// BufferedReader bufferedReader = new BufferedReader(new
		// InputStreamReader(
		// new FileInputStream(f)));
		// while ((bufferedReader.readLine()) != null) {
		String fileNameOrUri = args[0];
		Model model = ModelFactory.createDefaultModel();
		InputStream is = FileManager.get().open(fileNameOrUri);
		if (is != null) {
			model.read(is, null, "N-TRIPLE");
			StmtIterator iter = model.listStatements();
			while (iter.hasNext()) {
				Statement stmt = iter.nextStatement();
				Property predicate = stmt.getPredicate();
				if (predicate.getURI().equals("http://lobid.org/vocab/lobid#fulltextOnline")) {
					RDFNode object = stmt.getObject();
					if (object instanceof Resource) {
						testURL(object.toString());
					} // else object is a literal
						// RDFWriter writer = model.getWriter("N-TRIPLE");
						// model.write(System.out,"N-TRIPLE");
				}
			}
		} else {
			System.err.println("cannot read " + fileNameOrUri);
		}
	}

	// bufferedReader.close();
	// }

	/**
	 * Checks even if there is a recursive "HTTP moved permanently" structure.
	 * 
	 * @param links
	 */
	static public String testURL(String link) {
		now = System.currentTimeMillis();
		URL url = null;
		String ACCESSIBILITY_STATUS;

		int httpCode = 0;
		if ((ACCESSIBILITY_STATUS = lc.checkURL(link))
				.equals(Linkchecker.ACCESSIBLE_URL_HTTP_MOVED_PERM)) {
			url = lc.getPermanentMovedURL(link);
			while (true) {
				if ((httpCode = lc.getHTTPConnectionStatus(url)) == HttpURLConnection.HTTP_OK) {
					ACCESSIBILITY_STATUS = Linkchecker.ACCESSIBLE_URL_HTTP_OK;
					logger.log(Level.INFO, link + " is an "
							+ Linkchecker.ACCESSIBLE_URL_HTTP_MOVED_PERM + " to " + url.toString());
					break;
				} else {
					if (httpCode != HttpURLConnection.HTTP_MOVED_PERM) {
						ACCESSIBILITY_STATUS = Linkchecker.ACCESSIBLE_URL_HTTP_ERROR + " "
								+ httpCode;
						break;
					}
				}
				url = lc.getPermanentMovedURL(url);
			}
		}
		after = System.currentTimeMillis();
		String STATUS_URL = link;
		// if the URl was moved permanently we want this moved URL
		if (url != null) {
			STATUS_URL = url.toExternalForm();
		}
		logger.log(Level.INFO, "Status of URL '" + STATUS_URL + "' is " + ACCESSIBILITY_STATUS
				+ ", checked in " + (after - now) + " ms");
		return ACCESSIBILITY_STATUS;
	}
}