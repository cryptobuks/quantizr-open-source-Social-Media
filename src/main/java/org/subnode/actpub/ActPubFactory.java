package org.subnode.actpub;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.subnode.actpub.model.AP;
import org.subnode.actpub.model.APList;
import org.subnode.actpub.model.APOCreate;
import org.subnode.actpub.model.APOMention;
import org.subnode.actpub.model.APONote;
import org.subnode.actpub.model.APObj;

@Controller
public class ActPubFactory {
	@Autowired
	public ActPubService apService;

	@Autowired
	public ActPubUtil apUtil;

	@Autowired
	public ActPubCache apCache;

	private static final Logger log = LoggerFactory.getLogger(ActPubFactory.class);

	/**
	 * Creates a new 'note' message
	 */
	public APObj newCreateMessageForNote(List<String> toUserNames, String fromActor, String inReplyTo, String content,
			String noteUrl, boolean privateMessage, APList attachments) {
		ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
		// log.debug("sending note from actor[" + fromActor + "] inReplyTo[" + inReplyTo);
		return newCreateMessage(
				newNoteObject(toUserNames, fromActor, inReplyTo, content, noteUrl, now, privateMessage, attachments), fromActor,
				toUserNames, noteUrl, now);
	}

	/**
	 * Creates a new 'note' object
	 */
	public APONote newNoteObject(List<String> toUserNames, String attributedTo, String inReplyTo, String content, String noteUrl,
			ZonedDateTime now, boolean privateMessage, APList attachments) {
		APONote ret = new APONote();

		ret.put(AP.id, noteUrl);
		ret.put(AP.published, now.format(DateTimeFormatter.ISO_INSTANT));
		ret.put(AP.attributedTo, attributedTo);
		ret.put(AP.summary, null);
		ret.put(AP.url, noteUrl);
		ret.put(AP.sensitive, false);
		ret.put(AP.content, content);

		LinkedList<String> toList = new LinkedList<>();
		LinkedList<String> ccList = new LinkedList<>();

		APList tagList = new APList();
		for (String userName : toUserNames) {
			APObj webFinger = apUtil.getWebFinger(userName);
			String actorUrl = apUtil.getActorUrlFromWebFingerObj(webFinger);

			/*
			 * For public messages Mastodon puts the "Public" target in 'to' and the mentioned users in 'cc', so
			 * we do that same thing
			 */
			if (privateMessage) {
				toList.add(actorUrl);
			} else {
				ccList.add(actorUrl);
			}
			tagList.val(new APOMention() //
					.put(AP.href, actorUrl) //
					.put(AP.name, "@" + userName)); // prepend character to make it like '@user@server.com'
		}
		ret.put(AP.tag, tagList);

		if (!privateMessage) {
			toList.add(APConst.CONTEXT_STREAMS + "#Public");

			/*
			 * public posts should always cc the followers of the person doing the post (the actor pointed to by
			 * attributedTo)
			 */
			APObj actor = apCache.actorsByUrl.get(attributedTo);
			if (actor != null) {
				ccList.add(AP.str(actor, AP.followers));
			}
		}

		ret.put(AP.to, toList);

		if (ccList.size() > 0) {
			ret.put(AP.cc, ccList);
		}

		ret.put(AP.attachment, attachments);
		return ret;
	}

	/*
	 * Need to check if this works using the 'to and cc' arrays that are the same as the ones built
	 * above (in newNoteObject() function)
	 */
	public APOCreate newCreateMessage(APObj object, String fromActor, List<String> toActors, String noteUrl, ZonedDateTime now) {
		String idTime = String.valueOf(now.toInstant().toEpochMilli());
		APOCreate ret = new APOCreate();

		// this 'id' was an early WAG, and needs a fresh look now that AP code is more complete.
		ret.put(AP.id, noteUrl + "&apCreateTime=" + idTime);
		ret.put(AP.actor, fromActor);
		ret.put(AP.published, now.format(DateTimeFormatter.ISO_INSTANT));
		ret.put(AP.object, object);

		ret.put(AP.to, new APList() //
				.vals(toActors) //
				.val(APConst.CONTEXT_STREAMS + "#Public"));

		// LinkedList<String> ccArray = new LinkedList<>();
		// ccArray.add("https://www.w3.org/ns/activitystreams#Public");
		// ret.put("cc", ccArray);
		return ret;
	}
}
