import java.net.MalformedURLException;
import java.net.Socket;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.xbill.DNS.*;

/**
 * Checks if an URL is of scheme http[s]. Gets the port number. Makes a reverse
 * DNS lookup. Pings the host. Caches parts of the URL (not the whole URL,
 * because that is tp specific, there will not be many doublette URLs. Returns
 * permanently moved URLs.
 * 
 * This checker could no do wonders, however, i.e. if a site returns a status
 * code of 200 but in the body of the html is to read "error: page not found"
 * the checking must be incorrect. Readd my blog post at
 * http://www.dr0i.de/lib/2011/03/23/publisher_make_urls_useless.html
 * 
 * 
 * @author <a href="mailto:christoph@hbz-nrw.de">Pascal Christoph</a>
 * @date 2011.03.04
 */
public class Linkchecker {

	private int URL_CONNECTION_TIMEOUT;
	private int PING_TIMEOUT;
	public final static String NO_HTTP_OR_NO_HTTPS_URL = "no http[s] URL";
	public final static String NON_ACCESSIBLE_URL_UNKNOWN_HOST = "non-accessible URL, unknown host";
	public final static String NON_ACCESSIBLE_URL_PORT_BLOCKED = "non-accessible URL, port blocked";
	public final static String ACCESSIBLE_URL_HTTP_OK = "accessible URL, HTTP OK";
	public final static String ACCESSIBLE_URL_HTTP_ERROR = "accessible URL, HTTP error";
	public final static String ACCESSIBLE_URL_HTTP_MOVED_PERM = "accessible URL, HTTP moved permanently";
	Logger logger = Logger.getLogger(Linkchecker.class.getName());
	HashMap<String, String> CACHE_PROTOCOL_HOSTS_PORTS;

	/**
	 * Default url connection timeout is set to 2000, default ping timeout is
	 * set to 300.
	 */
	public Linkchecker() {
		CACHE_PROTOCOL_HOSTS_PORTS = new HashMap<String, String>();
		this.URL_CONNECTION_TIMEOUT = 2000;
		this.PING_TIMEOUT = 300;
	}

	/**
	 * Initializes with given timeouts.
	 * 
	 * @param url_connection_timeout
	 * @param ping_timeout
	 */
	public Linkchecker(int url_connection_timeout, int ping_timeout) {
		CACHE_PROTOCOL_HOSTS_PORTS = new HashMap<String, String>();
		this.URL_CONNECTION_TIMEOUT = url_connection_timeout;
		this.PING_TIMEOUT = ping_timeout;
	}

	/**
	 * Checks if the scheme, host and port of the URL is already cached. If the
	 * status is not HTTP_OK, return this status in index [0]. Otherwise checks
	 * whole URL. if it was not cached, check the URL status and the whole URL.
	 * If an URL is permanetly redirected, store this URL in index [1].
	 * 
	 * @param link
	 * @return [0]: the http-status ; [1] the permanently redirected URL if any,
	 *         else null
	 */
	public String checkURL(String link) {
		InetAddress INET_ADDR;
		URL url;
		String URL_HOST = null;
		try {
			url = new URL(link);
			URL_HOST = url.getHost();
		} catch (MalformedURLException ex) {
			return NON_ACCESSIBLE_URL_UNKNOWN_HOST;
		}
		// test if already cached
		String URL_PROTOCOL_HOST_PORT = url.getProtocol() + "://" + URL_HOST;
		int PORT = getPort(url);
		if (PORT == -1) {
			return NO_HTTP_OR_NO_HTTPS_URL;
		}
		URL_PROTOCOL_HOST_PORT = new String(URL_PROTOCOL_HOST_PORT + ":" + PORT);
		if (CACHE_PROTOCOL_HOSTS_PORTS.containsKey(URL_PROTOCOL_HOST_PORT)) {
			logger.log(Level.FINE, "Host and port of URL already checked: '"
					+ URL_PROTOCOL_HOST_PORT + "'");
			String value = CACHE_PROTOCOL_HOSTS_PORTS.get(URL_PROTOCOL_HOST_PORT);
			if (!value.equals(ACCESSIBLE_URL_HTTP_OK)) {
				return value;
			}
		} else { // end test if already cached
			try {
				INET_ADDR = Address.getByName(URL_HOST);
			} catch (Exception ex) {
				CACHE_PROTOCOL_HOSTS_PORTS.put(URL_PROTOCOL_HOST_PORT,
						NON_ACCESSIBLE_URL_UNKNOWN_HOST);
				return NON_ACCESSIBLE_URL_UNKNOWN_HOST;
			}
			try {
				if (!isAccessibleViaReverseDns(INET_ADDR)) {
					CACHE_PROTOCOL_HOSTS_PORTS.put(URL_PROTOCOL_HOST_PORT,
							NON_ACCESSIBLE_URL_UNKNOWN_HOST);
					return NON_ACCESSIBLE_URL_UNKNOWN_HOST;
				}
			} catch (IOException ex) {
				CACHE_PROTOCOL_HOSTS_PORTS.put(URL_PROTOCOL_HOST_PORT,
						NON_ACCESSIBLE_URL_UNKNOWN_HOST);
				return NON_ACCESSIBLE_URL_UNKNOWN_HOST;
			}

			if (!isReachable(INET_ADDR, PORT)) {
				CACHE_PROTOCOL_HOSTS_PORTS.put(URL_PROTOCOL_HOST_PORT + ":" + url.getPort(),
						NON_ACCESSIBLE_URL_PORT_BLOCKED);
				return NON_ACCESSIBLE_URL_PORT_BLOCKED;
			}
			CACHE_PROTOCOL_HOSTS_PORTS.put(URL_PROTOCOL_HOST_PORT, ACCESSIBLE_URL_HTTP_OK);
		}
		// test whole URL
		int http_code = 0;
		if ((http_code = getHTTPConnectionStatus(url)) == HttpURLConnection.HTTP_OK) {
			return ACCESSIBLE_URL_HTTP_OK;
		} else {
			if ((http_code = getHTTPConnectionStatus(url)) == HttpURLConnection.HTTP_MOVED_PERM) {
				return ACCESSIBLE_URL_HTTP_MOVED_PERM;
			} else {
				return ACCESSIBLE_URL_HTTP_ERROR + " " + http_code;
			}
		}
	}

	/**
	 * DNS reverse lookup.
	 * 
	 * @param url
	 * @return
	 * @throws IOException
	 */
	public boolean isAccessibleViaReverseDns(InetAddress addr) throws IOException {
		Resolver res = new ExtendedResolver();
		Name name = ReverseMap.fromAddress(addr);
		int type = Type.PTR;
		int dclass = DClass.IN;
		Record rec = Record.newRecord(name, type, dclass);
		Message query = Message.newQuery(rec);
		Message response = res.send(query);
		Record[] answers = response.getSectionArray(Section.ANSWER);
		if (answers.length == 0) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * InetAddress.isReachable(timeout) is not sufficient because port 7 is
	 * sometimes blocked by firewalls
	 * 
	 * @param inetAddress
	 * @param port
	 * @return
	 */
	public boolean isReachable(InetAddress inetAddress, int port) {
		return isReachable(inetAddress, port, this.PING_TIMEOUT);
	}

	/**
	 * InetAddress.isReachable(timeout) is not sufficient because port 7 is
	 * sometimes blocked by firewalls
	 * 
	 * @param inetAddress
	 * @param port
	 * @param timeout
	 * @return
	 */
	public boolean isReachable(InetAddress inetAddress, int port, int timeout) {
		boolean pingable = false;
		Socket socket = new Socket();
		try {
			InetSocketAddress inetSocket = new InetSocketAddress(inetAddress, port);
			socket.connect(inetSocket, timeout);
			pingable = true;
		} catch (SocketTimeoutException ex) {
			logger.log(Level.INFO,
					" Socket timeout on adress " + inetAddress.getCanonicalHostName() + ":" + port);
		} catch (IOException ex) {
			Logger.getLogger(Linkchecker.class.getName()).log(Level.INFO,
					"IOException on" + inetAddress.getCanonicalHostName() + ":" + port);
		} finally {
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
				}
			}
		}
		return pingable;
	}

	/**
	 * This method makes use of HttpURLConnection which will follow up to five
	 * HTTP redirects, following rfc2616. It will follow redirects from one
	 * origin server to another. This implementation doesn't follow redirects
	 * from HTTPS to HTTP or vice versa. The http connection of the status code
	 * will be returned.
	 * 
	 * @param url
	 * @return http status code
	 */
	public int getHTTPConnectionStatus(URL url) {
		return getHTTPConnectionStatus(url, false, 0);
	}

	/**
	 * This method makes use of HttpURLConnection. The
	 * "follow permanent moved redirection"-parameter can be set to false, so
	 * HTTP Permanent Moved Redirects will not be followed. Other redirections
	 * are followed, however, according to rfc2616 only up to five HTTP
	 * redirects, from one origin server to another. This implementation doesn't
	 * follow redirects from HTTPS to HTTP or vice versa. The http connection of
	 * the status code will be returned.
	 * 
	 * @param url
	 * @param follow
	 *            permanent moved redirection
	 * @param the
	 *            counter of the redirection loop. If >5 then the method
	 *            returns.
	 * @return http status code
	 */
	public int getHTTPConnectionStatus(URL url, boolean FOLLOW_PERMANENT_MOVED_REDIRECTION,
			int REDIRECTION_LOOPS) {
		HttpURLConnection urlConnection = null;
		try {
			urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.setInstanceFollowRedirects(FOLLOW_PERMANENT_MOVED_REDIRECTION);
			urlConnection.setRequestMethod("GET"); // GET
			urlConnection.setConnectTimeout(this.URL_CONNECTION_TIMEOUT); /*
																		 * timeout
																		 * after
																		 * 2s if
																		 * can't
																		 * connect
																		 */
			urlConnection.connect();
			urlConnection.getInputStream(); // neccesary , try using
											// 'http://google.de'
			int HTTP_CODE = urlConnection.getResponseCode();
			if (HTTP_CODE > 301 && HTTP_CODE < 400 && HTTP_CODE != 301 && REDIRECTION_LOOPS <= 5) {
				return getHTTPConnectionStatus(urlConnection.getURL(), true, REDIRECTION_LOOPS++);
			}
			REDIRECTION_LOOPS = 0;
			return urlConnection.getResponseCode();
		} catch (IOException e) {
			try {
				return urlConnection.getResponseCode();
			} catch (IOException ex) {
				return -1;
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			return -1;
		}

		finally {
			if (urlConnection != null) {
				urlConnection.disconnect();

			}
		}
	}

	public URL getPermanentMovedURL(String link) {
		try {
			return getPermanentMovedURL(new URL(link));
		} catch (MalformedURLException ex) {
			Logger.getLogger(Linkchecker.class.getName()).log(Level.WARNING, null, ex);
		}
		return null;
	}

	/**
	 * Gets the URL when it is permanently moved.
	 * 
	 * @param url
	 * @return the permamently moved URL or null
	 */
	public URL getPermanentMovedURL(URL url) {
		HttpURLConnection urlConnection = null;
		try {
			urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.setRequestMethod("GET");
			urlConnection.setConnectTimeout(this.URL_CONNECTION_TIMEOUT);
			urlConnection.connect();
			urlConnection.getInputStream();
			if ((getHTTPConnectionStatus(url)) == HttpURLConnection.HTTP_MOVED_PERM) {
				return urlConnection.getURL();
			} else {
				return null;
			}
		} catch (IOException e) {
			return null;
		} finally {
			if (urlConnection != null) {
				urlConnection.disconnect();
			}
		}
	}

	/**
	 * The default echo port is set to 7 according to RFC 862. Many Firewalls
	 * block this port, so a normal ping will not produce an answer from the
	 * server. Because of that, we ping not the port 7 but the port in question.
	 * Takes an URL. Determines port. Gets the IP of the domain and checks if
	 * the port on that IP is reachable.
	 * 
	 * @param url
	 * @param INET_ADDR
	 * @return true if pingable
	 * @throws UnknownHostException
	 */
	public boolean isPingableHttpOrHttpsURL(URL url, InetAddress INET_ADDR) {
		int PORT = getPort(url);
		return isReachable(INET_ADDR, PORT, this.PING_TIMEOUT);
	}

	/**
	 * Determines the port of an http[s]-URL - if there was no port number given
	 * the default one will be used.
	 * 
	 * @2do ftp
	 * 
	 * @param url
	 * @return the portnumber
	 */
	public int getPort(URL url) {
		int port = -1;
		String uriScheme = url.getProtocol().toLowerCase();
		// sometimes non standard ports are used , e.g. http://...:8080
		if (uriScheme.equals("http")) {
			if ((port = url.getPort()) == -1) {
				port = 80;
			}
		} else {
			if (uriScheme.equals("https")) {
				if ((port = url.getPort()) == -1) {
					port = 443;
				}
			} /*
			 * else { if (uriScheme.equals("ftp")) { if ((port = url.getPort())
			 * == -1) { port = 21; } else { return false; } } }
			 */
		}
		return port;
	}

	public int getUrlConnectionTimeout() {
		return this.URL_CONNECTION_TIMEOUT;
	}

	public int getPingTimeout() {
		return this.PING_TIMEOUT;
	}
}
