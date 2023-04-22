package quanta.service;

import org.apache.commons.collections4.map.LRUMap;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.model.client.OpenGraph;
import quanta.request.GetOpenGraphRequest;
import quanta.response.GetOpenGraphResponse;

@Component
@Slf4j
public class OpenGraphService extends ServiceBase {
	public final LRUMap<String, OpenGraph> ogCache = new LRUMap(1000);

	public static final String BROWSER_USER_AGENT =
			"Browser: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36";

	public GetOpenGraphResponse getOpenGraph(GetOpenGraphRequest ogReq) {
		GetOpenGraphResponse res = new GetOpenGraphResponse();
		OpenGraph openGraph = null;

		// if the url is cached (even if null) then return whatever's in the cache
		synchronized (ogCache) {
			if (ogCache.containsKey(ogReq.getUrl())) {
				openGraph = ogCache.get(ogReq.getUrl());
				res.setOpenGraph(openGraph);
				return res;
			}
		}

		try {
			openGraph = parseOpenGraph(ogReq.getUrl());
		} catch (Exception e) {
			String mime = attach.getMimeTypeFromUrl(ogReq.getUrl());
			openGraph = new OpenGraph();
			openGraph.setMime(mime);
		}

		// we allow storing a null if we got back a null. Cache it so we don't try again.
		synchronized (ogCache) {
			ogCache.put(ogReq.getUrl(), openGraph);
		}

		res.setOpenGraph(openGraph);
		return res;
	}

	public OpenGraph parseOpenGraph(String urlStr) throws Exception {
		// log.debug("JSoup parsing URL: " + urlStr);
		OpenGraph openGraph = new OpenGraph();
		Connection con = Jsoup.connect(urlStr);

		/*
		 * this browseragent thing is important to trick servers into sending us the LARGEST versions of the
		 * images
		 */
		con.userAgent(BROWSER_USER_AGENT);
		Document doc = con.get();

		// todo-2: add site_name, type, url, twitter:url, twitter:card (like og:type)

		openGraph.setTitle(getOg(doc, "og:title"));
		if (openGraph.getTitle() == null) {
			openGraph.setTitle(getOg(doc, "twitter:title"));
		}

		openGraph.setUrl(getOg(doc, "og:url"));
		if (openGraph.getUrl() == null) {
			openGraph.setUrl(getOg(doc, "twitter:url"));
		}

		openGraph.setDescription(getOg(doc, "og:description"));
		if (openGraph.getDescription() == null) {
			openGraph.setDescription(getOg(doc, "twitter:description"));
		}

		openGraph.setImage(getOg(doc, "og:image"));
		if (openGraph.getImage() == null) {
			openGraph.setImage(getOg(doc, "twitter:image"));
		}
		return openGraph;
	}

	private String getOg(Document doc, String prop) {
		Elements elm = doc.select("meta[property=" + prop + "]");
		return elm != null ? elm.attr("content") : null;
	}
}
