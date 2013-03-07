import org.junit.Test;
import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Logger;

import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.util.FileManager;

/**
 * @author <a href="mailto:christoph@hbz-nrw.de">Pascal Christoph</a>
 * @date 2011.03.08
 */
public class LinkcheckerOnlineTest {

	Linkchecker lc;
	Logger logger = Logger.getLogger(LinkcheckerOnlineTest.class.getName());
	String ACCESSIBILITY_STATUS = null;
	long now;
	long after;

	// @Test
	public void checkLinksInFile() throws IOException {
		this.lc = new Linkchecker();
		System.out.println("We are here: " + (new File(".")).getAbsolutePath());
		File f = new File("src/test/resources/urls.txt");
		String s;
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(
				new FileInputStream(f)));
		while ((s = bufferedReader.readLine()) != null) {
			String fileNameOrUri = "src/test/resources/rdfexample.ntriple";
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
						if (object instanceof Resource) { //do not check literals
							StartLinkchecker.testURL(object.toString());
						} 
					}
				}
			} else {
				System.err.println("cannot read " + fileNameOrUri);
			}
		}
		bufferedReader.close();
	}

	@Test
	public void testLinkchecker() {
		this.lc = new Linkchecker();
		HashMap<String, String> links = new HashMap<String, String>();
		links.put(
				"http://ug-subcd-erlcl.cd.sub.uni-goettingen.de:8595/webspirs/start.ws?databases=ECON",
				Linkchecker.NON_ACCESSIBLE_URL_PORT_BLOCKED);
		links.put("hbz-nrw.de/", Linkchecker.NON_ACCESSIBLE_URL_UNKNOWN_HOST);
		links.put("http://hbz-nrw.de/", Linkchecker.NON_ACCESSIBLE_URL_UNKNOWN_HOST);
		links.put("http://www.hbz-nrw.de/", Linkchecker.ACCESSIBLE_URL_HTTP_OK);
		links.put("http://lobid.org", Linkchecker.ACCESSIBLE_URL_HTTP_OK);
		links.put("http://google.de/webhp?hl=en", Linkchecker.ACCESSIBLE_URL_HTTP_OK);
		links.put("ftp://www.hbz-nrw.de/", Linkchecker.NO_HTTP_OR_NO_HTTPS_URL);
		links.put("http://google.de", Linkchecker.ACCESSIBLE_URL_HTTP_OK);
		links.put("http://lobid.org/ghjkgh", Linkchecker.ACCESSIBLE_URL_HTTP_ERROR + " 404");
		links.put("http://pascal.selfip.org/Paradigmenbildung/doc/MA_PChristoph.pdf",
				Linkchecker.ACCESSIBLE_URL_HTTP_OK);
		testAllURLS(links);
	}

	private void testAllURLS(HashMap<String, String> links) {
		Iterator<String> it = links.keySet().iterator();
		while (it.hasNext()) {
			String link = it.next();
			ACCESSIBILITY_STATUS = StartLinkchecker.testURL(link);
			assertEquals(links.get(link), ACCESSIBILITY_STATUS);
		}
	}

}
