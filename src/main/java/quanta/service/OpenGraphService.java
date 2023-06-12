
package quanta.service;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import quanta.config.ServiceBase;
import quanta.model.client.NodeProp;
import quanta.model.client.OpenGraph;
import quanta.mongo.model.SubNode;
import quanta.request.GetOpenGraphRequest;
import quanta.response.GetOpenGraphResponse;
import quanta.util.XString;

@Component
public class OpenGraphService extends ServiceBase {
	Pattern urlPattern = Pattern.compile("(https?:\\/\\/[^\\s]+)", Pattern.CASE_INSENSITIVE);

	private static Logger log = LoggerFactory.getLogger(OpenGraphService.class);
	public final LRUMap<String, OpenGraph> ogCache = new LRUMap(1000);
	public static final String BROWSER_USER_AGENT =
			"Browser: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36";

	public GetOpenGraphResponse getOpenGraph(GetOpenGraphRequest ogReq) {
		GetOpenGraphResponse res = new GetOpenGraphResponse();
		res.setOpenGraph(getOpenGraph(ogReq.getUrl()));
		return res;
	}

	public OpenGraph getOpenGraph(String url) {
		url = XString.stripIfEndsWith(url, "/");
		url = XString.stripIfEndsWith(url, "\\");
		
		// if the url is cached (even if null) then return whatever's in the cache
		synchronized (ogCache) {
			if (ogCache.containsKey(url)) {
				return ogCache.get(url);
			}
		}

		OpenGraph openGraph = null;
		try {
			openGraph = parseOpenGraph(url);
		} catch (Exception e) {
			String mime = attach.getMimeTypeFromUrl(url);
			openGraph = new OpenGraph();
			openGraph.setMime(mime);
		}

		// we can't trust what we get back from servers, but we do need to be sure URL is correct here
		// ourselves.
		openGraph.setUrl(url);

		// we allow storing a null if we got back a null. Cache it so we don't try again.
		synchronized (ogCache) {
			ogCache.put(url, openGraph);
		}

		return openGraph;
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

	// Parsese the content for any HTML links and attempts to get the OpenGraph from the network
	// and puts the opengraph object into node properties.
	//
	// todo-1: for now this method is 'cumulative' and never removes unused OG entries like if a node
	// is edited, but we will take care of that when we are calling this during SAVEs.
	public void parseNode(SubNode node) {
		ArrayList<String> ogList = (ArrayList<String>) node.getObj(NodeProp.OPEN_GRAPH.s(), ArrayList.class);

		if (StringUtils.isEmpty(node.getContent()))
			return;

		// Adding the " " to the end is a hack because my regex isn't perfect (todo-1: fix the regex)
		Matcher matcher = urlPattern.matcher(node.getContent()+" ");

		while (matcher.find()) {
			if (ogList == null) {
				ogList = new ArrayList<>();
			}
			String url = node.getContent().substring(matcher.start(0), matcher.end(0));

			// Stripping slashes is a hack because my regex isn't perfect (todo-1: fix the regex)
			url = XString.stripIfEndsWith(url, "/");
			url = XString.stripIfEndsWith(url, "\\");

			// set load=false if we already have this URL in our ogList
			boolean load = true;
			for (String urlCheck : ogList) {
				// just finding the URL is a hack but will be fine for now, to avoid parsing JSON
				if (urlCheck.contains(url)) {
					load = false;
					break;
				}
			}
			if (!load)
				continue;

			OpenGraph og = getOpenGraph(url);
			ogList.add(XString.compactPrint(og));

			// if more than 50 links in content then ignore the rest
			if (ogList.size() > 50) {
				break;
			}
		}

		node.set(NodeProp.OPEN_GRAPH.s(), ogList);
	}
}
