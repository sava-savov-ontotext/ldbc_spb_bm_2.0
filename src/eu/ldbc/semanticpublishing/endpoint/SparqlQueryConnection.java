package eu.ldbc.semanticpublishing.endpoint;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Used to prepare an HttpUrlConnection for executing a SPARQL query against a remote endpoint
 */
public class SparqlQueryConnection {

	public enum QueryType {
		SELECT, CONSTRUCT, DESCRIBE, INSERT, UPDATE, DELETE
	}
	protected String endpointUrl;
	protected String endpointUpdateUrl;
	protected String contentTypeForGraphQuery;
	protected int timeoutMilliseconds;
	protected AtomicReference<HttpURLConnection> httpUrlConnection = new AtomicReference<>(null);
	protected boolean verbose;

	protected String queryString;
	protected QueryType queryType;
	protected String DEFAULT_RESULT_TYPE = "application/sparql-results+xml";

	public static final String SPARQL_STAR_RESULT_TYPE = "application/x-sparqlstar-results+json";
	/**
	 * Constructs a SparqlQueryConnection and writes query string to out stream
	 */
	public SparqlQueryConnection(String endpointUrl, String endpointUpdateUrl, String contentTypeForGraphQuery, String queryString, QueryType queryType, int timeoutMilliseconds, boolean verbose) {
		this.endpointUrl = endpointUrl;
		this.endpointUpdateUrl = endpointUpdateUrl;
		this.contentTypeForGraphQuery = contentTypeForGraphQuery;
		this.timeoutMilliseconds = timeoutMilliseconds;
		this.verbose = verbose;
		this.queryString = queryString;
		this.queryType = queryType;
		prepareConnection(true);
	}
	
	/**
	 * Constructs a SparqlQueryConnection without writing query to out stream 
	*/
	public SparqlQueryConnection(String endpointUrl, String endpointUpdateUrl, String contentTypeForGraphQuery, int timeoutMilliseconds, boolean verbose) {
		this(endpointUrl, endpointUpdateUrl, contentTypeForGraphQuery, "", QueryType.CONSTRUCT, timeoutMilliseconds, verbose);
		prepareConnection(false);
	}

	private void connect() throws IOException {
		httpUrlConnection.get().connect();
	}

	private InputStream getResponse() throws IOException {

		getResponseCode(httpUrlConnection.get());

		return httpUrlConnection.get().getInputStream();

	}

	public InputStream execute() throws IOException {
		connect();
		return getResponse();
	}

	public void disconnect() {
		httpUrlConnection.get().disconnect();
		httpUrlConnection = new AtomicReference<>(null);
	}

	protected void setOutputStream() throws IOException {
		OutputStream outStream = httpUrlConnection.get().getOutputStream();
		if (isSparqlUpdate()) {
			outStream.write("update=".getBytes());
		}
		else {
			outStream.write("query=".getBytes());
		}
		outStream.write(URLEncoder.encode(queryString, "UTF-8").getBytes());
		outStream.flush();
		outStream.close();
	}

	public void prepareConnection(boolean setQueryToStream) {
		try {
			httpUrlConnection.set((HttpURLConnection) createURL().openConnection());
			httpUrlConnection.get().setDoOutput(true);
			httpUrlConnection.get().setDefaultUseCaches(false);
			httpUrlConnection.get().setUseCaches(false);
			httpUrlConnection.get().setReadTimeout(timeoutMilliseconds);
			httpUrlConnection.get().setConnectTimeout(timeoutMilliseconds);

			if (isSparqlUpdate()) {
				httpUrlConnection.get().setRequestMethod("POST");
				httpUrlConnection.get().setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
				httpUrlConnection.get().setRequestProperty("Accept", "*/*");
			} else {
				httpUrlConnection.get().setRequestMethod("POST");
				httpUrlConnection.get().setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
				if (isGraphQuery()) {
					httpUrlConnection.get().setRequestProperty("Accept", contentTypeForGraphQuery);
				} else {
					httpUrlConnection.get().setRequestProperty("Accept", DEFAULT_RESULT_TYPE);
				}
			}
			if (setQueryToStream && !queryString.isEmpty()) {
				setOutputStream();
			}
		} catch (UnsupportedEncodingException uee) {
			System.out.println("SparqlQueryConnection : UnsupportedEncodingException : " + uee.getMessage());
			uee.printStackTrace();
		} catch (IOException ioe) {
			//http://docs.oracle.com/javase/6/docs/technotes/guides/net/http-keepalive.html
			System.out.println("SparqlQueryConnection : IOException : " + ioe.getMessage());
			try {
				InputStream es = httpUrlConnection.get().getErrorStream();
	
				byte[] buffer = new byte[10000];
				// read the response body			
				while ((es.read(buffer)) > 0) {
					//consume ErrorStream's contents
				}
				es.close();
			} catch (IOException e) {
				//sink the exception, not interested if error stream produces it.
				e.printStackTrace();
			}
		}
	}

	protected boolean isSparqlUpdate() {
		return queryType == QueryType.INSERT || queryType == QueryType.UPDATE || queryType == QueryType.DELETE;
	}

	protected boolean isGraphQuery() {
		return queryType == QueryType.DESCRIBE || queryType == QueryType.CONSTRUCT;
	}

	protected String prepareEncodedUrlQueryString() throws IOException {
		if (isSparqlUpdate()) {
			return endpointUpdateUrl;
		} else {
			return endpointUrl;
		}
	}

	protected void getResponseCode(HttpURLConnection httpUrlConnection) throws IOException {
		int code = httpUrlConnection.getResponseCode();
		if ((code < 200 || code >= 300) && verbose) {
			System.out.println("SparqlQueryConnection : received error code : " + code + " from server. Error message : " + httpUrlConnection.getResponseMessage());
		}
	}

	protected URL createURL() throws IOException {
		return new URL(prepareEncodedUrlQueryString());
	}

	public String getQueryString() {
		return this.queryString;
	}

	public void setQueryString(String queryString) {
		this.queryString = queryString;
	}

	public QueryType getQueryType() {
		return this.queryType;
	}

	public void setQueryType(QueryType queryType) {
		this.queryType = queryType;
	}

	public void setResultType(String RESULT_TYPE) {
		this.DEFAULT_RESULT_TYPE = RESULT_TYPE;
	}
}
