
package quanta.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CharSequenceReader;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.rometools.modules.content.ContentModuleImpl;
import com.rometools.modules.itunes.EntryInformationImpl;
import com.rometools.modules.mediarss.MediaEntryModuleImpl;
import com.rometools.modules.mediarss.types.MediaContent;
import com.rometools.modules.mediarss.types.MediaGroup;
import com.rometools.modules.mediarss.types.Metadata;
import com.rometools.modules.mediarss.types.Thumbnail;
import com.rometools.rome.feed.module.DCModuleImpl;
import com.rometools.rome.feed.module.Module;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.SyndFeedOutput;
import com.rometools.rome.io.XmlReader;
import quanta.AppServer;
import quanta.config.ServiceBase;
import quanta.model.NodeMetaInfo;
import quanta.model.client.PrincipalName;
import quanta.model.client.RssFeed;
import quanta.model.client.RssFeedEnclosure;
import quanta.model.client.RssFeedEntry;
import quanta.model.client.RssFeedMediaContent;
import quanta.mongo.MongoRepository;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.request.GetMultiRssRequest;
import quanta.response.GetMultiRssResponse;
import quanta.util.Const;
import quanta.util.DateUtil;
import quanta.util.ExUtil;
import quanta.util.LimitedInputStreamEx;
import quanta.util.StreamUtil;
import quanta.util.Util;
import quanta.util.XString;
/* Proof of Concept RSS Publishing */
@Component
public class RSSFeedService extends ServiceBase {
	
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RSSFeedService.class);
	private static boolean refreshingCache = false;
	private static final Object policyLock = new Object();
	PolicyFactory policy = null;
	private boolean USE_HTTP_READER = false;
	private boolean USE_URL_READER = false;
	private boolean USE_SPRING_READER = true;
	private static final RestTemplate restTemplate = new RestTemplate(Util.getClientHttpRequestFactory(10000));
	/*
	 * Cache of all feeds.
	 */
	private static final ConcurrentHashMap<String, SyndFeed> feedCache = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<Integer, String> feedNameOfItem = new ConcurrentHashMap<>();
	/*
	 * keep track of which feeds failed so we don't try them again until another 30-min cycle
	 */
	private static final HashSet<String> failedFeeds = new HashSet<>();
	/*
	 * Cache of all aggregates
	 */
	private static final ConcurrentHashMap<String, SyndFeed> aggregateCache = new ConcurrentHashMap<>();
	private static int MAX_CACHE_SIZE = 500;
	public static final LinkedHashMap<String, byte[]> proxyCache = new LinkedHashMap<String, byte[]>(MAX_CACHE_SIZE + 1, 0.75F, false) {
		protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
			return size() > MAX_CACHE_SIZE;
		}
	};
	private static final int MAX_FEED_ITEMS = 75;
	private static final int REFRESH_FREQUENCY_MINS = 180; // 3 hrs
	static boolean run = false;

	/*
	 * Runs immediately at startup, and then every few minutes, to refresh the feedCache.
	 */
	@Scheduled(fixedDelay = REFRESH_FREQUENCY_MINS * 60 * 1000)
	public void run() {
		if (run || !prop.isDaemonsEnabled() || !MongoRepository.fullInit) return;
		try {
			run = true;
			if (AppServer.isShuttingDown() || !AppServer.isEnableScheduling()) {
				log.debug("ignoring RSSFeedService schedule cycle");
				return;
			}
			log.debug("RSSFeedService.refreshFeedCache");
			refreshFeedCache();
			aggregateCache.clear();
			proxyCache.clear();
		} finally {
			run = false;
		}
	}

	public String refreshFeedCache() {
		if (refreshingCache) {
			return "Cache refresh was already in progress.";
		}
		try {
			refreshingCache = true;
			int count = 0;
			int fails = 0;
			if (failedFeeds.size() > 0) {
				List<String> failedFeedsList = new LinkedList<>(failedFeeds);
				failedFeeds.clear();
				for (String url : failedFeedsList) {
					log.debug("Retrying previously failed feed: " + url);
					SyndFeed feed = getFeed(url, false);
					if (feed != null) {
						count++;
					} else {
						fails++;
					}
				}
			}
			for (String url : feedCache.keySet()) {
				log.debug("Refreshing feed: " + url);
				SyndFeed feed = getFeed(url, false);
				if (feed != null) {
					count++;
				} else {
					fails++;
				}
			}
			return "Refreshed " + String.valueOf(count) + " feeds. (Fail Count: " + String.valueOf(fails) + ")";
		} finally {
			refreshingCache = false;
		}
	}

	public void aggregateFeeds(List<String> urls, List<SyndEntry> entries, int page) {
		try {
			for (String url : urls) {
				// log.debug("Processing Feed: " + url);
				SyndFeed inFeed = getFeed(url, true);
				if (inFeed != null) {
					for (SyndEntry entry : inFeed.getEntries()) {
						if (entry.getPublishedDate() != null) {
							entries.add(entry);
						} else {
							// log.debug("ENTRY: Missing Pub Date: " + XString.prettyPrint(entry));
						}
					}
				}
			}
			entries.sort((s1, s2) -> s2.getPublishedDate().compareTo(s1.getPublishedDate()));
			/*
			 * Now from the complete 'entries' list we extract out just the page we need into 'pageEntires' and
			 * then stuff pageEntries back into 'entries' to send out of this method
			 */
			List<SyndEntry> pageEntries = new LinkedList<>();
			int pageNo = page - 1;
			int startIdx = pageNo * MAX_FEED_ITEMS;
			int idx = 0;
			for (SyndEntry entry : entries) {
				if (idx >= startIdx) {
					pageEntries.add(entry);
					if (pageEntries.size() >= MAX_FEED_ITEMS) {
						break;
					}
				}
				idx++;
			}
			entries.clear();
			entries.addAll(pageEntries);
		} catch (Exception e) {
			ExUtil.error(log, "Error: ", e);
		}
	}

	public SyndFeed getFeed(final String url, boolean fromCache) {
		// log.debug("getFeed: " + url);
		/*
		 * if this feed failed don't try it again. Whenever we DO force the system to try a feed again
		 * that's done by wiping failedFeeds clean but this 'getFeed' method should just bail out if the
		 * feed has failed
		 */
		if (fromCache && failedFeeds.contains(url)) {
			// if the feed has failed at least attempt to get from the cache whatever the latest is that we have
			return feedCache.get(url);
		}
		Reader reader = null;
		try {
			SyndFeed inFeed = null;
			if (fromCache) {
				inFeed = feedCache.get(url);
				if (inFeed != null) {
					// log.debug("CACHE FEED HIT: " + url); // XString.prettyPrint(inFeed));
					return inFeed;
				}
			}
			int timeout = 60; // seconds
			if (USE_URL_READER) {
				/*
				 * This is not a memory leak that we don't close the connection. This is correct. No need to close
				 */
				URLConnection conn = new URL(url).openConnection();
				conn.setConnectTimeout(timeout * 1000);
				conn.setReadTimeout(timeout * 1000);
				reader = new XmlReader(conn);
				SyndFeedInput input = new SyndFeedInput();
				inFeed = input.build(reader);
			}
			/*
			 * I was experimenting this this way of getting a reader as a last attempt to get a specific
			 * problematic URL to work, that keeps causing a timeout when I try to read from it thru the server
			 * side, even though the same url works fine when entered into my browser url, so one trick that has
			 * worked in the past was to masquerade as a browser using the 'user agent'. So this code DOES work,
			 * but never did solve the problem with that one specific URL that simply refuses to send data to
			 * the Quanta server.
			 * 
			 * UPDATE: I'm leaving the long explanation above, but once I tried the code inside
			 * USE_SPRING_READER=true, block suddenly all the RSS feeds no longer have any timeout issues. My
			 * best theory for why is that my restTemplate is doing something special that fixes these issues.
			 */
			if (USE_HTTP_READER) {
				RequestConfig config =  //
				//
				//
				RequestConfig.custom().setConnectTimeout(timeout * 1000).setConnectionRequestTimeout(timeout * 1000).setSocketTimeout(timeout * 1000).build();
				HttpClient client = HttpClientBuilder.create().setDefaultRequestConfig(config).build();
				HttpGet request = new HttpGet(url);
				request.addHeader("User-Agent", Const.FAKE_USER_AGENT);
				HttpResponse response = client.execute(request);
				InputStream is = response.getEntity().getContent();
				LimitedInputStreamEx limitedIs = new LimitedInputStreamEx(is, 100 * Const.ONE_MB);
				byte[] buffer = IOUtils.toByteArray(limitedIs);
				reader = new CharSequenceReader(new String(buffer));
				SyndFeedInput input = new SyndFeedInput();
				inFeed = input.build(reader);
			}
			if (USE_SPRING_READER) {
				log.debug("rss network read: " + url);
				inFeed = restTemplate.execute(url, HttpMethod.GET, null, response -> {
					SyndFeedInput input = new SyndFeedInput();
					try {
						return input.build(new XmlReader(new LimitedInputStreamEx(response.getBody(), 100 * Const.ONE_MB)));
					} catch (FeedException e) {
						throw new IOException("Could not parse response", e);
					}
				});
			}
			// another example from online (that I've never tried):
			// try (CloseableHttpClient client = HttpClients.createMinimal()) {
			// HttpUriRequest request = new HttpGet(url);
			// try (CloseableHttpResponse response = client.execute(request);
			// InputStream stream = response.getEntity().getContent()) {
			// SyndFeedInput input = new SyndFeedInput();
			// SyndFeed feed = input.build(new XmlReader(stream));
			// System.out.println(feed.getTitle());
			// }
			// }
			// log.debug("CACHE MISS. Queried Feed " + url + " has " + inFeed.getEntries().size() + "
			// entries.");
			// we update the cache regardless of 'fromCache' val. this is correct.
			feedCache.put(url, inFeed);
			// store knowledge of which feed Title goes with each entry instance.
			if (inFeed.getEntries() != null) {
				for (SyndEntry se : inFeed.getEntries()) {
					feedNameOfItem.put(se.hashCode(), inFeed.getTitle());
				}
			}
			return inFeed;
		} catch (Exception e) {
			/*
			 * Leave feedCache with any existing mapping it has when it fails. Worst case here is a stale cache
			 * remains in place rather than getting forgotten just because it's currently unavailable
			 *
			 * This error can happen a lot since feeds out on the wild are so chaotic so we won't bother to
			 * clutter our logs with a stack trace here, and just log the message.
			 * 
			 * todo-2: Actually it would be better to put this entire string being logged here into a hashset to
			 * just keep a unique list, and not even log it here, but make it part of the 'systemInfo' available
			 * under the admin menu for checking server status info.
			 */
			log.debug("Error reading feed: " + url + " -> " + e.getMessage());
			failedFeeds.add(url);
			// if the feed has failed at least attempt to get from the cache whatever the latest is that we have
			return feedCache.get(url);
		} finally {
			if (reader != null) {
				StreamUtil.close(reader);
			}
		}
	}

	private String quoteFix(String html) {
		html = html.replace("&#8221;", "\'");
		html = html.replace("&#8220;", "\'");
		// Warning these ARE two different characters, even though they look the same.
		html = html.replace("’", "\'");
		html = html.replace("‘", "\'");
		// special kinds of dashes
		html = html.replace("–", "--");
		return html;
	}

	// See also: https://github.com/OWASP/java-html-sanitizer
	private String sanitizeHtml(String html) {
		if (StringUtils.isEmpty(html)) return html;
		// this sanitizer seems to choke on these special quotes so replace them first.
		html = quoteFix(html);
		if (policy == null) {
			synchronized (policyLock) {
				/*
				 * I have removed IMAGES only because it looks silly when we display an image that's also displayed
				 * as part of the feed formatting
				 */
				policy = /* .and(Sanitizers.IMAGES) *///
				Sanitizers.FORMATTING.and(Sanitizers.BLOCKS).and(Sanitizers.LINKS).and(Sanitizers.STYLES).and(Sanitizers.TABLES);
			}
		}
		html = policy.sanitize(html);
		if (html.length() > 1000) {
			html = html.substring(0, 1000) + "...";
		}
		return html;
	}

	public GetMultiRssResponse getMultiRssFeed(GetMultiRssRequest req) {
		GetMultiRssResponse res = new GetMultiRssResponse();
		// parse out list of URLs, and remove commented lines
		List<String> urlList = XString.tokenize(req.getUrls(), "\n", true);
		urlList.removeIf(url -> url.startsWith("#") || StringUtils.isEmpty(url.trim()));
		SyndFeed feed = null;
		/* If multiple feeds we build an aggregate */
		if (urlList.size() > 1) {
			feed = new SyndFeedImpl();
			feed.setEncoding("UTF-8");
			feed.setFeedType("rss_2.0");
			feed.setTitle("");
			feed.setDescription("");
			feed.setAuthor("");
			feed.setLink("");
			List<SyndEntry> entries = new LinkedList<>();
			feed.setEntries(entries);
			aggregateFeeds(urlList, entries, req.getPage());
		} else 
		/* If not an aggregate return the one external feed itself */
		{
			String url = urlList.get(0);
			SyndFeed cachedFeed = getFeed(url, true);
			if (cachedFeed != null) {
				feed = new SyndFeedImpl();
				cloneFeedForPage(feed, cachedFeed, req.getPage());
			}
		}
		if (feed != null) {
			fixFeed(feed);
			boolean addFeedTitles = urlList.size() > 1;
			RssFeed rssFeed = convertToFeed(feed, addFeedTitles);
			// log.debug("FEED JSON: " + XString.prettyPrint(rssFeed));
			res.setFeed(rssFeed);
		}
		return res;
	}

	public RssFeed convertToFeed(SyndFeed feed, boolean addFeedTitles) {
		RssFeed rf = new RssFeed();
		// log.debug("convertToFeed: title=" + feed.getTitle());
		rf.setTitle(feed.getTitle());
		rf.setDescription(sanitizeHtml(feed.getDescription()));
		rf.setAuthor(feed.getAuthor());
		rf.setEncoding(feed.getEncoding());
		if (feed.getImage() != null) {
			rf.setImage(feed.getImage().getUrl());
		}
		// I was trying to get the "image" for peter schiff's Feed (not items, but feed itself), and this
		// was my first attempt, and it didn't work. Not sure if his feed is bad or what. Will come back to
		// this later, RSS is good enough for now. todo-2.
		// processModules(feed, rf);
		rf.setLink(feed.getLink());
		List<RssFeedEntry> rssEntries = new LinkedList<>();
		rf.setEntries(rssEntries);
		if (feed.getEntries() != null) {
			for (SyndEntry entry : feed.getEntries()) {
				// log.debug("Entry: " + XString.prettyPrint(entry));
				RssFeedEntry e = new RssFeedEntry();
				if (addFeedTitles) {
					e.setParentFeedTitle(feedNameOfItem.get(entry.hashCode()));
				}
				try {
					if (processEntry(entry, e)) {
						rssEntries.add(e);
					}
				} catch (Exception ex) {
				}
				// if anything goes wrong processing the entry, we can ignore it and continue with the next entry.
			}
		}
		return rf;
	}

	private boolean processEntry(SyndEntry entry, RssFeedEntry e) {
		// log.debug("entry: " + entry.getTitle());
		if (entry.getDescription() != null) {
			e.setDescription(sanitizeHtml(entry.getDescription().getValue()));
		}
		e.setTitle(entry.getTitle());
		e.setLink(entry.getLink());
		if (entry.getPublishedDate() != null) {
			e.setPublishDate(DateUtil.shortFormatDate(entry.getPublishedDate().getTime()));
		} else {
			// log.debug("RSS ENTRY: Missing Pub Date: " + XString.prettyPrint(entry));
		}
		e.setAuthor(entry.getAuthor());
		if (entry.getContents() != null) {
			for (SyndContent content : entry.getContents()) {
				e.setDescription(sanitizeHtml(content.getValue()));
			}
		}
		// DO NOT DELETE
		// Don't know of use cases for this yet. Leaving as FYI.
		// List<Element> foreignMarkups = entry.getForeignMarkup();
		// for (Element foreignMarkup : foreignMarkups) {
		// String imgURL = foreignMarkup.getAttribute("url").getValue();
		// }
		if (entry.getEnclosures() != null) {
			List<RssFeedEnclosure> enclosures = new LinkedList<>();
			e.setEnclosures(enclosures);
			for (SyndEnclosure enc : entry.getEnclosures()) {
				RssFeedEnclosure re = new RssFeedEnclosure();
				re.setType(enc.getType());
				re.setUrl(enc.getUrl());
				enclosures.add(re);
			}
		}
		processModules(entry, e);
		return true;
	}

	private void processModules(SyndFeed entry, RssFeed e) {
		if (entry.getModules() != null) {
			for (Module m : entry.getModules()) {
				// log.debug("Module: " + m.getClass().getName());
				if (m instanceof MediaEntryModuleImpl) {
					MediaEntryModuleImpl mm = (MediaEntryModuleImpl) m;
					if (mm.getMediaContents() != null) {
						// put new list on return object
						List<RssFeedMediaContent> mediaContent = new LinkedList<>();
						// add mediaContent to RssFeed ?
						// e.setMediaContent(mediaContent);
						// process all media contents
						for (MediaContent mc : mm.getMediaContents()) {
							RssFeedMediaContent rfmc = new RssFeedMediaContent();
							rfmc.setType(mc.getType());
							rfmc.setUrl(mc.getReference().toString());
							rfmc.setMedium(mc.getMedium());
							mediaContent.add(rfmc);
						}
					}
					if (mm.getMediaGroups() != null) {
						for (MediaGroup mg : mm.getMediaGroups()) {
							Metadata md = mg.getMetadata();
							if (md != null) {
								if (md.getDescription() != null) {
									e.setDescription(sanitizeHtml(md.getDescription()));
								}
								if (md.getEmbed() != null) {
									log.debug("Metadata Embed Url: " + md.getEmbed().getUrl());
								}
								if (md.getThumbnail() != null) {
									for (Thumbnail tn : mg.getMetadata().getThumbnail()) {
										e.setImage(tn.getUrl().toASCIIString());
									}
								}
							} else {
								log.debug("MediaGroup has no metadata.");
							}
						}
					} else {
						log.debug("media has no groups.");
					}
				} else if (m instanceof ContentModuleImpl) {
				} else 
				// ContentModuleImpl contentMod = (ContentModuleImpl) m;
				// if (ok(contentMod.getContents() )) {
				// for (String contents : contentMod.getContents()) {
				// log.debug("CI.contents: " + contents);
				// }
				// }
				// if (ok(contentMod.getContentItems() )) {
				// for (ContentItem ci : contentMod.getContentItems()) {
				// log.debug("CI.encoding: " + ci.getContentEncoding());
				// log.debug("CI.format: " + ci.getContentFormat());
				// log.debug("CI.value: " + ci.getContentValue());
				// log.debug("CI.url: " + ci.getContentResource());
				// }
				// }
				if (m instanceof EntryInformationImpl) {
					EntryInformationImpl itunesMod = (EntryInformationImpl) m;
					if (itunesMod.getImage() != null) {
						try {
							e.setImage(itunesMod.getImage().toURI().toString());
						} catch (Exception e1) {
						}
					} else 
					// ignore
					{
						e.setImage(itunesMod.getImageUri());
					}
					if (!StringUtils.isEmpty(itunesMod.getTitle())) {
						e.setTitle(itunesMod.getTitle());
					}
					// e.setSubTitle(itunesMod.getSubtitle());
					if (!StringUtils.isEmpty(itunesMod.getSummary())) {
						e.setDescription(sanitizeHtml(itunesMod.getSummary()));
					}
				} else 
				// what feeds use this? (todo-2)
				if (m instanceof DCModuleImpl) {
				} else 
				// DCModuleImpl dm = (DCModuleImpl) m;
				// String dcFormat = dm.getFormat();
				// String dcSource = dm.getSource();
				// String dcTitle = dm.getTitle();
				// log.debug("dcSource: " + dcSource);
				{
					log.debug("Unknown module type: " + m.getClass().getName());
				}
			}
		}
	}

	private void processModules(SyndEntry entry, RssFeedEntry e) {
		if (entry.getModules() != null) {
			for (Module m : entry.getModules()) {
				// log.debug("Module: " + m.getClass().getName());
				if (m instanceof MediaEntryModuleImpl) {
					MediaEntryModuleImpl mm = (MediaEntryModuleImpl) m;
					if (mm.getMediaContents() != null) {
						// put new list on return object
						List<RssFeedMediaContent> mediaContent = new LinkedList<>();
						e.setMediaContent(mediaContent);
						// process all media contents
						for (MediaContent mc : mm.getMediaContents()) {
							RssFeedMediaContent rfmc = new RssFeedMediaContent();
							rfmc.setType(mc.getType());
							rfmc.setUrl(mc.getReference().toString());
							rfmc.setMedium(mc.getMedium());
							mediaContent.add(rfmc);
						}
					}
					if (mm.getMediaGroups() != null) {
						for (MediaGroup mg : mm.getMediaGroups()) {
							Metadata md = mg.getMetadata();
							if (md != null) {
								if (md.getDescription() != null) {
									e.setDescription(sanitizeHtml(md.getDescription()));
								}
								if (md.getEmbed() != null) {
									log.debug("Metadata Embed Url: " + md.getEmbed().getUrl());
								}
								if (md.getThumbnail() != null) {
									for (Thumbnail tn : md.getThumbnail()) {
										e.setThumbnail(tn.getUrl().toASCIIString());
									}
								}
							} else {
								log.debug("MediaGroup has no metadata.");
							}
						}
					} else {
						log.debug("media has no groups.");
					}
					Metadata md = mm.getMetadata();
					if (md != null) {
						if (md.getDescription() != null) {
							e.setDescription(sanitizeHtml(md.getDescription()));
						}
						if (md.getThumbnail() != null) {
							for (Thumbnail tn : md.getThumbnail()) {
								e.setThumbnail(tn.getUrl().toASCIIString());
							}
						}
					}
				} else if (m instanceof ContentModuleImpl) {
				} else 
				// ContentModuleImpl contentMod = (ContentModuleImpl) m;
				// if (ok(contentMod.getContents() )) {
				// for (String contents : contentMod.getContents()) {
				// log.debug("CI.contents: " + contents);
				// }
				// }
				// if (ok(contentMod.getContentItems() )) {
				// for (ContentItem ci : contentMod.getContentItems()) {
				// log.debug("CI.encoding: " + ci.getContentEncoding());
				// log.debug("CI.format: " + ci.getContentFormat());
				// log.debug("CI.value: " + ci.getContentValue());
				// log.debug("CI.url: " + ci.getContentResource());
				// }
				// }
				if (m instanceof EntryInformationImpl) {
					EntryInformationImpl itunesMod = (EntryInformationImpl) m;
					if (itunesMod.getImage() != null) {
						try {
							e.setImage(itunesMod.getImage().toURI().toString());
						} catch (Exception e1) {
						}
					} else 
					// ignore
					{
						e.setImage(itunesMod.getImageUri());
					}
					if (!StringUtils.isEmpty(itunesMod.getTitle())) {
						e.setTitle(itunesMod.getTitle());
					}
					e.setSubTitle(itunesMod.getSubtitle());
					if (!StringUtils.isEmpty(itunesMod.getSummary())) {
						e.setDescription(sanitizeHtml(itunesMod.getSummary()));
					}
				} else 
				// what feeds use this? (todo-2)
				if (m instanceof DCModuleImpl) {
				} else 
				// DCModuleImpl dm = (DCModuleImpl) m;
				// String dcFormat = dm.getFormat();
				// String dcSource = dm.getSource();
				// String dcTitle = dm.getTitle();
				// log.debug("dcSource: " + dcSource);
				{
					log.debug("Unknown module type: " + m.getClass().getName());
				}
			}
		}
	}

	/*
	 * Makes feed be a cloned copy of cachedFeed but with only the specific 'page' of results extracted
	 */
	private void cloneFeedForPage(SyndFeed feed, SyndFeed cachedFeed, int page) {
		feed.setEncoding(cachedFeed.getEncoding());
		feed.setFeedType(cachedFeed.getFeedType());
		feed.setTitle(cachedFeed.getTitle());
		feed.setDescription(cachedFeed.getDescription());
		feed.setAuthor(cachedFeed.getAuthor());
		feed.setLink(cachedFeed.getLink());
		feed.setImage(cachedFeed.getImage());
		List<SyndEntry> entries = new LinkedList<>();
		feed.setEntries(entries);
		// make page zero-offset before using.
		int pageNo = page - 1;
		int startIdx = pageNo * MAX_FEED_ITEMS;
		int idx = 0;
		for (SyndEntry entry : cachedFeed.getEntries()) {
			if (idx >= startIdx) {
				entries.add(entry);
				if (entries.size() >= MAX_FEED_ITEMS) {
					break;
				}
			}
			idx++;
		}
	}

	public void getRssFeed(MongoSession ms, String nodeId, Writer writer) {
		SubNode node = read.getNode(ms, nodeId);
		SyndFeed feed = new SyndFeedImpl();
		feed.setEncoding("UTF-8");
		feed.setFeedType("rss_2.0");
		NodeMetaInfo metaInfo = snUtil.getNodeMetaInfo(node);
		feed.setTitle(metaInfo.getTitle() != null ? metaInfo.getTitle() : "");
		feed.setLink("");
		feed.setDescription(sanitizeHtml(metaInfo.getDescription() != null ? metaInfo.getDescription() : ""));
		List<SyndEntry> entries = new LinkedList<>();
		feed.setEntries(entries);
		if (AclService.isPublic(ms, node)) {
			Criteria crit = Criteria.where(SubNode.AC + "." + PrincipalName.PUBLIC.s()).ne(null);
			Iterable<SubNode> iter = read.getChildren(ms, node, Sort.by(Sort.Direction.ASC, SubNode.ORDINAL), null, 0, crit);
			if (iter != null) {
				for (SubNode n : iter) {
					if (!AclService.isPublic(ms, n)) continue;
					metaInfo = snUtil.getNodeMetaInfo(n);
					// Currently the link will be an attachment URL, but need to research how ROME
					// handles attachments.
					if (metaInfo.getAttachmentUrl() == null) {
						metaInfo.setAttachmentUrl(metaInfo.getUrl());
					}
					SyndEntry entry = new SyndEntryImpl();
					entry.setTitle(metaInfo.getTitle() != null ? metaInfo.getTitle() : "ID: " + n.getIdStr());
					entry.setLink(metaInfo.getAttachmentUrl() != null ? metaInfo.getAttachmentUrl() : prop.getProtocolHostAndPort());
					/*
					 * todo-2: need menu item "Set Create Time", and "Set Modify Time", that prompts with the datetime
					 * GUI, so publishers have more control over this in the feed, or else have an rssTimestamp as an
					 * optional property which can be set on any node to override this.
					 * 
					 * UPDATE: Now that we have 'date' property as a generic feature of nodes (calendar icon on edit
					 * dialog) we can use that as our publish time here, and allow that to be the override for the date
					 * on the node.
					 */
					entry.setPublishedDate(n.getCreateTime());
					SyndContent description = new SyndContentImpl();
					/*
					 * todo-2: NOTE: I tried putting some HTML into 'content' as a test and setting the mime type, but
					 * it doesn't render correctly, so I just need to research how to get HTML in RSS descriptions, but
					 * this is low priority for now so I'm not doing it yet.
					 * 
					 * todo-2: NOTE: when org.owasp.html.Sanitizers capability was added, I forgot to revisit this, so I
					 * need to check what I'm doing here and see if we need "HTML" now here instead.
					 */
					description.setType("text/plain");
					description.setType("text/html");
					description.setValue(sanitizeHtml(metaInfo.getDescription() != null ? metaInfo.getDescription() : ""));
					entry.setDescription(description);
					entries.add(entry);
				}
			}
		}
		writeFeed(feed, writer);
	}

	private void fixFeed(SyndFeed feed) {
		if (feed == null) return;
		if (StringUtils.isEmpty(feed.getEncoding())) feed.setEncoding("UTF-8");
		if (StringUtils.isEmpty(feed.getFeedType())) feed.setFeedType("rss_2.0");
		if (StringUtils.isEmpty(feed.getTitle())) feed.setTitle("");
		if (StringUtils.isEmpty(feed.getDescription())) feed.setDescription("");
		if (StringUtils.isEmpty(feed.getAuthor())) feed.setAuthor("");
		if (StringUtils.isEmpty(feed.getLink())) feed.setLink("");
	}

	private void writeFeed(SyndFeed feed, Writer writer) {
		if (writer != null && feed != null) {
			try {
				fixFeed(feed);
				SyndFeedOutput output = new SyndFeedOutput();
				boolean prettyPrint = true;
				String feedStr = output.outputString(feed, prettyPrint);
				feedStr = convertStreamChars(feedStr);
				// log.debug("FEED XML: " + feedStr);
				writer.write(feedStr);
			} catch (Exception e) {
				ExUtil.error(log, "writeFeed Error: ", e);
				throw new RuntimeException("internal server error");
			}
		}
	}

	/*
	 * Lots of feeds have characters that won't display nicely in HTML so we fix that here
	 */
	private String convertStreamChars(String s) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c < 128) {
				sb.append(c);
			} else {
				switch (c) {
				case '—': 
					sb.append("-");
					break;
				case '”': 
					sb.append("\"");
					break;
				case '’': 
					sb.append("\'");
					break;
				default: 
					sb.append(" ");
					break;
				}
			}
		}
		return sb.toString();
	}
	// DO NOT DELETE - this is the code to convert to HTML
	// private String convertMarkdownToHtml() {
	// MutableDataSet options = new MutableDataSet();
	// options.set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create(),
	// TocExtension.create()));
	// options.set(TocExtension.LEVELS, TocOptions.getLevels(1, 2, 3, 4, 5, 6));
	// // This numbering works in the TOC but I haven't figured out how to number
	// the
	// // actual headings in the body of the document itself.
	// // options.set(TocExtension.IS_NUMBERED, true);
	// Parser parser = Parser.builder(options).build();
	// HtmlRenderer renderer = HtmlRenderer.builder(options).build();
	// recurseNode(exportNode, 0);
	// Node document = parser.parse(markdown.toString());
	// String body = renderer.render(document);
	// }
}
