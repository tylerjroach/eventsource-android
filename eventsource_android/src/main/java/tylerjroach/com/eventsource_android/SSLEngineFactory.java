package tylerjroach.com.eventsource_android;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

public class SSLEngineFactory {
	SSLEngine GetNewSSLEngine() {
		SSLEngine sslEngine = null;
		SSLContext sslContext;
		try {
			sslContext = SSLContext.getInstance("TLS");
			try {
				sslContext.init(null, null, null);
				sslEngine = sslContext.createSSLEngine();
			} catch (KeyManagementException e) {
				return null;
			}
		} catch (NoSuchAlgorithmException e1) {
			return null;
		}
		return sslEngine;
	}
}
