package quanta.actpub;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import quanta.actpub.model.APOPerson;
import quanta.actpub.model.APObj;
import quanta.config.ServiceBase;
import quanta.exception.NodeAuthFailedException;
import quanta.model.client.PrincipalName;
import quanta.util.XString;

// @CrossOrigin is done by AppFilter.
/**
 * Main REST Controller endpoint for AP
 * 
 * Actor URLs: ${host}/u/clay/home
 * 
 * Actor IDs: ${host}/ap/u/clay
 */
@Controller
public class ActPubController extends ServiceBase {

	private static Logger log = LoggerFactory.getLogger(ActPubController.class);
	@Autowired
	private ActPubLog apLog;

	/**
	 * WebFinger GET
	 */
	@RequestMapping(value = APConst.PATH_WEBFINGER, method = RequestMethod.GET,
			produces = {APConst.CTYPE_JRD_JSON, APConst.CTYPE_ACT_JSON})
	@ResponseBody
	public Object webFinger(//
			@RequestParam(value = "resource", required = true) String resource, //
			HttpServletRequest req) {
		apLog.trace("getWebFinger: " + resource);
		APObj ret = apUtil.generateWebFinger(resource);
		if (ret != null) {
			HttpHeaders hdr = new HttpHeaders();
			setContentType(hdr, req, APConst.MTYPE_JRD_JSON);
			return new ResponseEntity<Object>(ret, hdr, HttpStatus.OK);
		}
		return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
	}

	/**
	 * Actor GET (redirect for Mastodon)
	 *
	 * Mastodon insists on using this format for the URL which is NOT what we have in our Actor object
	 * so they are breaking the spec and we tolerate it by redirecting
	 * 
	 * simple redirect from /ap/user/[userName] to /u/[userName]/home
	 * 
	 * todo-1: is this documented in the user guide about user being able to have a node named 'home'
	 * and what it means if they do? Also need to ensure this ALWAYS works especially in the AP
	 * scenarios and when user has done nothing themselves to create a 'home' node.
	 * 
	 * NOTE: This is returning the HTML of the HOME, not a JSON value
	 */
	@RequestMapping(value = "/ap/user/{userName}", method = RequestMethod.GET)
	public void mastodonGetUser(//
			@PathVariable(value = "userName", required = true) String userName, //
			HttpServletRequest req, //
			HttpServletResponse res) throws Exception {
		if (PrincipalName.ADMIN.s().equalsIgnoreCase(userName.trim()))
			return;
		String url = prop.getProtocolHostAndPort() + "/u/" + userName + "/home";
		apLog.trace("Redirecting to: " + url);
		res.sendRedirect(url);
	}

	/*
	 * This redirects HTTP requests by an ActorID to show the 'home' node of the user as html web page
	 */
	@RequestMapping(value = APConst.ACTOR_PATH + "/{userName}", method = RequestMethod.GET, produces = {APConst.CTYPE_HTML})
	public void getHTMLForUserId(//
			@PathVariable(value = "userName", required = true) String userName, //
			HttpServletRequest req, //
			HttpServletResponse res) throws Exception {
		if (PrincipalName.ADMIN.s().equalsIgnoreCase(userName.trim()))
			return;
		String url = prop.getProtocolHostAndPort() + "/u/" + userName + "/home";
		apLog.trace("Redirecting to: " + url);
		res.sendRedirect(url);
	}

	/**
	 * Actor GET
	 */
	@RequestMapping(value = APConst.ACTOR_PATH + "/{userName}", method = RequestMethod.GET,
			produces = {APConst.CTYPE_ACT_JSON, APConst.CTYPE_LD_JSON})
	@ResponseBody
	public Object actor(//
			@PathVariable(value = "userName", required = true) String userName, HttpServletRequest req) {
		if (PrincipalName.ADMIN.s().equalsIgnoreCase(userName.trim()))
			return null;
		apLog.trace("getActor: " + userName);
		APOPerson ret = apub.generatePersonObj(userName);
		if (ret != null) {
			HttpHeaders hdr = new HttpHeaders();
			setContentType(hdr, req, APConst.MTYPE_ACT_JSON);
			return new ResponseEntity<Object>(ret, hdr, HttpStatus.OK);
		}
		return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
	}

	//
	//
	//
	/**
	 * Shared Inbox POST
	 */
	@RequestMapping(value = APConst.PATH_INBOX, method = RequestMethod.POST,
			produces = {APConst.CTYPE_LD_JSON, APConst.CTYPE_ACT_JSON})
	@ResponseBody
	public Object sharedInboxPost(//
			@RequestBody byte[] body, //
			HttpServletRequest req) {
		try {
			ActPubService.inboxCount++;
			apub.processInboxPost(req, body);
			return new ResponseEntity<String>(HttpStatus.OK);
		} catch (Exception e) {
			return new ResponseEntity<String>(HttpStatus.BAD_REQUEST);
		}
	}

	//
	//
	//
	/**
	 * User Inbox POST
	 * 
	 * WARNING: This inbox and the Shared inbox (above) can both be called simultaneously in cases when
	 * someone is doing a public reply to a Quanta node, and so Mastodon sends out the public inbox post
	 * and the post to the user simultaneously.
	 */
	@RequestMapping(value = APConst.PATH_INBOX + "/{userName}", method = RequestMethod.POST,
			produces = {APConst.CTYPE_LD_JSON, APConst.CTYPE_ACT_JSON})
	@ResponseBody
	public Object inboxPost(//
			@RequestBody byte[] body, //
			@PathVariable(value = "userName", required = true) String userName, //
			HttpServletRequest httpReq) {
		if (PrincipalName.ADMIN.s().equalsIgnoreCase(userName.trim()))
			return null;
		try {
			ActPubService.inboxCount++;
			apub.processInboxPost(httpReq, body);
			return new ResponseEntity<String>(HttpStatus.OK);
		} catch (Exception e) {
			return new ResponseEntity<String>(HttpStatus.BAD_REQUEST);
		}
	}

	//
	//
	//
	/**
	 * GET JSON of object
	 */
	@RequestMapping(value = {"/"}, method = RequestMethod.GET, produces = {APConst.CTYPE_LD_JSON, APConst.CTYPE_ACT_JSON})
	@ResponseBody
	public Object getJsonObj(HttpServletRequest req, //
			@RequestParam(value = "id", required = false) String id) {
		try {
			APObj ret = apOutbox.getResource(req, id);
			if (ret != null) {
				HttpHeaders hdr = new HttpHeaders();
				setContentType(hdr, req, APConst.MTYPE_ACT_JSON);
				return new ResponseEntity<Object>(ret, hdr, HttpStatus.OK);
			} else {
				return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
			}
		} catch (NodeAuthFailedException nafe) {
			return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
		} catch (Exception e) {
			return new ResponseEntity<String>(HttpStatus.BAD_REQUEST);
		}
	}

	//
	//
	/**
	 * Outbox GET
	 */
	@RequestMapping(value = APConst.PATH_OUTBOX + "/{userName}", method = RequestMethod.GET,
			produces = {APConst.CTYPE_ACT_JSON, APConst.CTYPE_LD_JSON})
	@ResponseBody
	public Object outbox(//
			@PathVariable(value = "userName", required = true) String userName,
			@RequestParam(value = "min_id", required = false) String minId,
			@RequestParam(value = "page", required = false) String page, HttpServletRequest req) {
		if (PrincipalName.ADMIN.s().equalsIgnoreCase(userName.trim()))
			return null;
		APObj ret = null;
		if (APConst.TRUE.equals(page)) {
			ret = apOutbox.generateOutboxPage(req, userName, minId);
		} else {
			/*
			 * Mastodon calls this method, but never calls back in (to generateOutboxPage above) for any pages.
			 * I'm not sure if this is something we're doing wrong or what, because I don't know enough about
			 * what Mastodon is "supposed" to do, to be able to even say if this is incorrect or not.
			 * 
			 * From analyzing other 'server to server' calls on other Mastodon instances it seems like at least
			 * the "toot count" should be showing up, but when I search a local user (non-federated) and it gets
			 * the outbox, mastodon still shows "0 toots", even though it just queried my inbox and there ARE
			 * toots and we DID return the correct number of them.
			 */
			ret = apOutbox.generateOutbox(userName);
		}
		if (ret != null) {
			apLog.trace("Reply with Outbox: " + XString.prettyPrint(ret));
			HttpHeaders hdr = new HttpHeaders();
			setContentType(hdr, req, APConst.MTYPE_ACT_JSON);
			return new ResponseEntity<Object>(ret, hdr, HttpStatus.OK);
		} else {
			return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
		}
	}

	//
	//
	/**
	 * Followers GET
	 */
	@RequestMapping(value = APConst.PATH_FOLLOWERS + "/{userName}", method = RequestMethod.GET,
			produces = {APConst.CTYPE_ACT_JSON, APConst.CTYPE_LD_JSON})
	@ResponseBody
	public Object getFollowers(//
			@PathVariable(value = "userName", required = false) String userName,
			@RequestParam(value = "min_id", required = false) String minId,
			@RequestParam(value = "page", required = false) String page, HttpServletRequest req) {
		if (PrincipalName.ADMIN.s().equalsIgnoreCase(userName.trim()))
			return null;
		APObj ret = null;
		if (APConst.TRUE.equals(page)) {
			ret = apFollower.generateFollowersPage(userName, minId);
		} else {
			ret = apFollower.generateFollowers(null, userName);
		}
		if (ret != null) {
			apLog.trace("Reply with Followers: " + XString.prettyPrint(ret));
			HttpHeaders hdr = new HttpHeaders();
			setContentType(hdr, req, APConst.MTYPE_ACT_JSON);
			return new ResponseEntity<Object>(ret, hdr, HttpStatus.OK);
		} else {
			return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
		}
	}

	//
	//
	/**
	 * Following GET
	 */
	@RequestMapping(value = APConst.PATH_FOLLOWING + "/{userName}", method = RequestMethod.GET,
			produces = {APConst.CTYPE_ACT_JSON, APConst.CTYPE_LD_JSON})
	@ResponseBody
	public Object getFollowing(//
			@PathVariable(value = "userName", required = false) String userName,
			@RequestParam(value = "min_id", required = false) String minId,
			@RequestParam(value = "page", required = false) String page, HttpServletRequest req) {
		if (PrincipalName.ADMIN.s().equalsIgnoreCase(userName.trim()))
			return null;
		APObj ret = null;
		if (APConst.TRUE.equals(page)) {
			ret = apFollowing.generateFollowingPage(userName, minId);
		} else {
			ret = apFollowing.generateFollowing(null, userName);
		}
		if (ret != null) {
			apLog.trace("Reply with Following: " + XString.prettyPrint(ret));
			HttpHeaders hdr = new HttpHeaders();
			setContentType(hdr, req, APConst.MTYPE_ACT_JSON);
			return new ResponseEntity<Object>(ret, hdr, HttpStatus.OK);
		} else {
			return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
		}
	}

	//
	//
	/**
	 * Replies GET
	 */
	@RequestMapping(value = APConst.PATH_REPLIES + "/{nodeId}", method = RequestMethod.GET,
			produces = {APConst.CTYPE_ACT_JSON, APConst.CTYPE_LD_JSON})
	@ResponseBody
	public Object getReplies(//
			@PathVariable(value = "nodeId", required = true) String nodeId, HttpServletRequest req) {
		APObj ret = apReplies.generateReplies(nodeId);
		if (ret != null) {
			HttpHeaders hdr = new HttpHeaders();
			setContentType(hdr, req, APConst.MTYPE_ACT_JSON);
			return new ResponseEntity<Object>(ret, hdr, HttpStatus.OK);
		} else {
			return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
		}
	}

	/*
	 * We set in the header whatever it was the request called for, or else if none specified set to the
	 * default type
	 */
	private void setContentType(HttpHeaders hdr, HttpServletRequest req, MediaType defaultType) {
		if (req != null && !StringUtils.isEmpty(req.getContentType())) {
			hdr.setContentType(MediaType.valueOf(req.getContentType()));
		} else {
			hdr.setContentType(defaultType);
		}
	}
}
