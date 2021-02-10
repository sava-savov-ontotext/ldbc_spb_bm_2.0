package eu.ldbc.semanticpublishing.endpoint;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Used to prepare an HttpUrlConnection for executing a SPARQL query against a remote endpoint
 */
public class TransactionalQueryConnection extends SparqlQueryConnection {
	private boolean runQueriesInTransaction;
	private HttpURLConnection transactionConn;
	private AtomicReference<HttpURLConnection> commitTransactionConn = new AtomicReference<>(null);
	private final AtomicReference<String> txLocation = new AtomicReference<>("");

	/**
	 * Constructs a SparqlQueryConnection without writing query to out stream
	*/
	public TransactionalQueryConnection(String endpointUrl, String endpointUpdateUrl, String contentTypeForGraphQuery, int timeoutMilliseconds, boolean verbose) {
		super(endpointUrl, endpointUpdateUrl, contentTypeForGraphQuery, timeoutMilliseconds, verbose);
		prepareConnection(false);
	}

	@Override
	protected String prepareEncodedUrlQueryString() throws IOException {
		if (isSparqlUpdate() || !runQueriesInTransaction) {
			return super.prepareEncodedUrlQueryString();
		} else {
			URL url = new URL(endpointUrl + "/transactions");
			transactionConn = (HttpURLConnection) url.openConnection();
			transactionConn.setRequestMethod("POST");
			transactionConn.connect();
			getResponseCode(transactionConn);
			txLocation.set(transactionConn.getHeaderField("Location"));
			return txLocation.get() + "?action=QUERY";
		}
	}	

	@Override
	public void prepareConnection(boolean setQueryToStream) {

		try {
			if (isSparqlUpdate() || !runQueriesInTransaction) {
				super.prepareConnection(setQueryToStream);
			} else {
				httpUrlConnection.set((HttpURLConnection) createURL().openConnection());
				httpUrlConnection.get().setDoOutput(true);
				httpUrlConnection.get().setDefaultUseCaches(false);
				httpUrlConnection.get().setUseCaches(false);
				httpUrlConnection.get().setReadTimeout(timeoutMilliseconds);
				httpUrlConnection.get().setConnectTimeout(timeoutMilliseconds);
				httpUrlConnection.get().setRequestMethod("PUT");
				httpUrlConnection.get().setRequestProperty("Content-Type", "application/sparql-query");
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
				//int responseCode = httpUrlConnection.getResponseCode();
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

	@Override
	protected void setOutputStream() throws IOException {
		if (isSparqlUpdate() || !runQueriesInTransaction) {
			super.setOutputStream();
		}
		else {
			OutputStream outStream = httpUrlConnection.get().getOutputStream();
			outStream.write(queryString.getBytes());
			outStream.flush();
			outStream.close();
		}
	}

	public void commitTransaction() {
		if (!isSparqlUpdate() && runQueriesInTransaction) {
			try {
				URL url = new URL(txLocation.get() + "?action=COMMIT");
				commitTransactionConn.set((HttpURLConnection) url.openConnection());
				commitTransactionConn.get().setRequestMethod("PUT");
				commitTransactionConn.get().connect();
				getResponseCode(commitTransactionConn.get());
				commitTransactionConn.get().disconnect();
			} catch (IOException e) {
				System.err.println("Could not commit transaction " + txLocation.get());
			} finally {
				httpUrlConnection.get().disconnect();
			}
		}
	}

	public void setRunQueriesInTransaction(boolean runQueriesInTransaction) {
		this.runQueriesInTransaction = runQueriesInTransaction;
	}

	public boolean isRunQueriesInTransaction() {
		return runQueriesInTransaction;
	}
}
