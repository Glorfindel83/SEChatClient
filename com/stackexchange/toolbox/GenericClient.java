package com.stackexchange.toolbox;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public abstract class GenericClient implements Closeable {
	protected final CloseableHttpClient client = HttpClients.createDefault();
	protected final String email, password;

	public GenericClient(String propertiesFile) throws Exception {
		// Load configuration
		Properties properties = new Properties();
		properties.load(new FileInputStream(propertiesFile));
		email = properties.getProperty("email");
		password = properties.getProperty("password");
	}

	public void login(String loginURL) throws Exception {
		// Load login form
		HttpGet getRequest = new HttpGet(loginURL);
		String fkey;
		try (CloseableHttpResponse httpResponse = client.execute(getRequest);
				InputStream inputStream = httpResponse.getEntity().getContent()) {
			Document document = Jsoup.parse(inputStream, "UTF-8", loginURL);
			Element element = document.selectFirst("input[name='fkey']");
			fkey = element.val();
		}
		// Submit login form
		HttpPost postRequest = new HttpPost(loginURL);
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("fkey", fkey));
		params.add(new BasicNameValuePair("email", email));
		params.add(new BasicNameValuePair("password", password));
		postRequest.setEntity(new UrlEncodedFormEntity(params));
		try (CloseableHttpResponse httpResponse = client.execute(postRequest)) {
			// Store cookies
			getCookies(httpResponse);
		}
	}

	@Override
	public void close() throws IOException {
		if (client != null) {
			client.close();
		}
	}

	protected void getCookies(HttpResponse response) {
		cookieString = null;
		for (Header header : response.getHeaders("Set-Cookie")) {
			String value = header.getValue().split(";")[0];
			if (cookieString != null) {
				cookieString += "; ";
			} else {
				cookieString = "";
			}
			cookieString += value;
		}
	}

	protected void setCookies(HttpRequest request) {
		request.addHeader("Cookie", cookieString);
	}

	private String cookieString;
}
