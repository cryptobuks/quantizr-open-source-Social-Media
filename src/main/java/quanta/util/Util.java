package quanta.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Random;
import java.util.Scanner;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import lombok.extern.slf4j.Slf4j;
import quanta.AppController;
import quanta.model.client.PrincipalName;
import quanta.mongo.MongoRepository;

@Slf4j 
public class Util {
	private static final Random rand = new Random();

	public static boolean allowInsecureUrl(String url) {
		// return url.contains("/bin/profileHeader") || url.contains("/bin/avatar") || //
		// todo-1: this is a known and 'by design' security hole where we don't protect binaries from access [yet]
		return url.contains("/bin/") || //
				!url.contains(AppController.API_PATH);
	}

	public static boolean gracefulReadyCheck(ServletResponse res) throws RuntimeException, IOException {
		if (!MongoRepository.fullInit) {
			if (res instanceof HttpServletResponse) {
				try {
					((HttpServletResponse) res).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
				} catch (Exception e) {
					// silently ignore this exception.
				}
			} else {
				throw new RuntimeException("Server not yet started.");
			}
		}
		return MongoRepository.fullInit;
	}

	// supposedly in Java 17, we now have this: HexFormat.of().parseHex(s) that can replace this.
	public static byte[] hexStringToBytes(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
		}
		return data;
	}

	public static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public static boolean isSpringAuthenticated() {
		return SecurityContextHolder.getContext().getAuthentication() != null
				&& SecurityContextHolder.getContext().getAuthentication().isAuthenticated();

		// DO NOT DELETE (yet)
		// //when Anonymous Authentication is enabled
		// !(SecurityContextHolder.getContext().getAuthentication()
		// instanceof AnonymousAuthenticationToken)
	}

	/*
	 * Generates a very strong unguessable token. We could also use JWT here, but for our architecture
	 * the only requirement is unique and unguessable.
	 */
	static long counter = 1357;

	public static String genStrongToken() {
		return String.valueOf(Math.abs(++counter + (new Date().getTime()) ^ Math.abs(rand.nextLong())));
	}

	public static boolean equalObjs(Object o1, Object o2) {
		if (o1 == null && o2 == null)
			return true;
		if (o1 != null && o2 == null)
			return false;
		if (o2 != null && o1 == null)
			return false;
		return o1.equals(o2);
	}

	/*
	 * If addParam is non null it's expected to be something like "param=val" and will get added to any
	 * existing query string
	 */
	public static String getFullURL(HttpServletRequest request, String addParam) {
		String url = request.getRequestURL().toString();
		String query = request.getQueryString();

		// append to queryString if necessary.
		if (!StringUtils.isEmpty(addParam)) {
			if (!StringUtils.isEmpty(query)) {
				query += "&" + addParam;
			} else {
				query = "&" + addParam;
			}
		}

		if (query == null) {
			return url;
		} else {
			return url + "?" + query;
		}
	}

	// extracts mime from this type of url: data:image/png;base64,[data...]
	public static String getMimeFromDataUrl(String url) {
		int colonIdx = url.indexOf(":");
		int semiColonIdx = url.indexOf(";");
		String mime = url.substring(colonIdx + 1, semiColonIdx);
		return mime;
	}

	// timeout=0 means infinite timeout (no timeout)
	public static ClientHttpRequestFactory getClientHttpRequestFactory(int timeout) {
		HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
		clientHttpRequestFactory.setConnectionRequestTimeout(timeout);
		clientHttpRequestFactory.setConnectTimeout(timeout);
		clientHttpRequestFactory.setReadTimeout(timeout);
		return clientHttpRequestFactory;
	}

	public static HttpEntity<MultiValueMap<String, Object>> getBasicRequestEntity() {
		HttpHeaders headers = new HttpHeaders();
		MultiValueMap<String, Object> bodyMap = new LinkedMultiValueMap<>();
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(bodyMap, headers);
		return requestEntity;
	}

	// //other example from Baeldung
	// private ClientHttpRequestFactory getClientHttpRequestFactory() {
	// int timeout = 5000;
	// RequestConfig config = RequestConfig.custom()
	// .setConnectTimeout(timeout)
	// .setConnectionRequestTimeout(timeout)
	// .setSocketTimeout(timeout)
	// .build();
	// CloseableHttpClient client = HttpClientBuilder
	// .create()
	// .setDefaultRequestConfig(config)
	// .build();
	// return new HttpComponentsClientHttpRequestFactory(client);
	// }

	public static String extractTitleFromUrl(String url) {
		String title = null;
		InputStream is = null;
		Scanner scanner = null;

		long startTime = System.currentTimeMillis();
		try {
			int timeout = 20;
			RequestConfig config = RequestConfig.custom()//
					.setConnectTimeout(timeout * 1000) //
					.setConnectionRequestTimeout(timeout * 1000) //
					.setSocketTimeout(timeout * 1000).build();
			HttpClient client = HttpClientBuilder.create().setDefaultRequestConfig(config).build();
			HttpGet request = new HttpGet(url);
			request.addHeader("User-Agent", Const.FAKE_USER_AGENT);
			HttpResponse response = client.execute(request);

			log.debug("Response Code: " + response.getStatusLine().getStatusCode() + " reason="
					+ response.getStatusLine().getReasonPhrase());

			scanner = new Scanner(response.getEntity().getContent());
			String responseBody = scanner.useDelimiter("\\A").next();
			title = responseBody.substring(responseBody.indexOf("<title>") + 7, responseBody.indexOf("</title>"));
		} catch (Exception e) {
			log.error("*** ERROR reading url: " + url, e);
			return null;
		} finally {
			StreamUtil.close(scanner, is);
			log.info("Stream read took: " + (System.currentTimeMillis() - startTime) + "ms");
		}

		return title;

		// InputStream response = null;
		// try {
		// String url = "http://www.google.com";
		// response = new URL(url).openStream();

		// Scanner scanner = new Scanner(response);
		// String responseBody = scanner.useDelimiter("\\A").next();
		// System.out.println(
		// responseBody.substring(responseBody.indexOf("<title>") + 7,
		// responseBody.indexOf("</title>")));

		// } catch (IOException ex) {
		// ex.printStackTrace();
		// } finally {
		// try {
		// response.close();
		// } catch (IOException ex) {
		// ex.printStackTrace();
		// }
		// }
	}

	public static void failIfAdmin(String userName) {
		if (PrincipalName.ADMIN.s().equalsIgnoreCase(userName)) {
			throw new RuntimeException("Admin not allowed");
		}
	}
}
