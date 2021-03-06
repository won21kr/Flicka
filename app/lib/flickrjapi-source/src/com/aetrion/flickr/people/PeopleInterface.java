/*
 * Copyright (c) 2005 Aetrion LLC.
 */
package com.aetrion.flickr.people;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.aetrion.flickr.FlickrException;
import com.aetrion.flickr.Parameter;
import com.aetrion.flickr.RequestContext;
import com.aetrion.flickr.Response;
import com.aetrion.flickr.Transport;
import com.aetrion.flickr.auth.AuthUtilities;
import com.aetrion.flickr.groups.Group;
import com.aetrion.flickr.photos.Extras;
import com.aetrion.flickr.photos.PhotoList;
import com.aetrion.flickr.photos.PhotoUtils;
import com.aetrion.flickr.util.StringUtilities;
import com.aetrion.flickr.util.XMLUtilities;
import com.aetrion.flickr.FlickrCache;

/**
 * Interface for finding Flickr users.
 *
 * @author Anthony Eden
 * @version $Id: PeopleInterface.java,v 1.26 2009/07/11 20:30:27 x-mago Exp $
 */
public class PeopleInterface {

	public static final String METHOD_FIND_BY_EMAIL = "flickr.people.findByEmail";
	public static final String METHOD_FIND_BY_USERNAME = "flickr.people.findByUsername";
	public static final String METHOD_GET_INFO = "flickr.people.getInfo";
	public static final String METHOD_GET_ONLINE_LIST = "flickr.people.getOnlineList";
	public static final String METHOD_GET_PHOTOS = "flickr.people.getPhotos";
	public static final String METHOD_GET_PUBLIC_GROUPS = "flickr.people.getPublicGroups";
	public static final String METHOD_GET_PUBLIC_PHOTOS = "flickr.people.getPublicPhotos";
	public static final String METHOD_GET_UPLOAD_STATUS = "flickr.people.getUploadStatus";

	private final String apiKey;
	private final String sharedSecret;
	private final Transport transportAPI;
	private final FlickrCache flickrCache;

	public PeopleInterface(
			String apiKey,
			String sharedSecret,
			Transport transportAPI
	) {
		this.apiKey = apiKey;
		this.sharedSecret = sharedSecret;
		this.transportAPI = transportAPI;
		this.flickrCache = FlickrCache.getFlickrCache();
	}

	/**
	 * Find the user by their email address.
	 *
	 * This method does not require authentication.
	 *
	 * @param email The email address
	 * @return The User
	 * @throws IOException
	 * @throws SAXException
	 * @throws FlickrException
	 */
	public User findByEmail(String email) throws IOException, SAXException, FlickrException {
		List parameters = new ArrayList();
		parameters.add(new Parameter("method", METHOD_FIND_BY_EMAIL));
		parameters.add(new Parameter("api_key", apiKey));

		parameters.add(new Parameter("find_email", email));

		Response response = transportAPI.get(transportAPI.getPath(), parameters);
		if (response.isError()) {
			throw new FlickrException(response.getErrorCode(), response.getErrorMessage());
		}
		Element userElement = response.getPayload();
		User user = new User();
		user.setId(userElement.getAttribute("nsid"));
		user.setUsername(XMLUtilities.getChildValue(userElement, "username"));
		return user;
	}

	/**
	 * Find a User by the username.
	 *
	 * This method does not require authentication.
	 *
	 * @param username The username
	 * @return The User object
	 * @throws IOException
	 * @throws SAXException
	 * @throws FlickrException
	 */
	public User findByUsername(String username) throws IOException, SAXException, FlickrException {
		List parameters = new ArrayList();
		parameters.add(new Parameter("method", METHOD_FIND_BY_USERNAME));
		parameters.add(new Parameter("api_key", apiKey));

		parameters.add(new Parameter("username", username));

		Response response = transportAPI.get(transportAPI.getPath(), parameters);
		if (response.isError()) {
			throw new FlickrException(response.getErrorCode(), response.getErrorMessage());
		}
		Element userElement = response.getPayload();
		User user = new User();
		user.setId(userElement.getAttribute("nsid"));
		user.setUsername(XMLUtilities.getChildValue(userElement, "username"));
		return user;
	}

	/**
	 * Get info about the specified user.
	 *
	 * This method does not require authentication.
	 *
	 * @param userId The user ID
	 * @return The User object
	 * @throws IOException
	 * @throws SAXException
	 * @throws FlickrException
	 */
	public User getInfo(String userId) throws IOException, SAXException, FlickrException {
		List parameters = new ArrayList();
		parameters.add(new Parameter("method", METHOD_GET_INFO));
		parameters.add(new Parameter("api_key", apiKey));

		parameters.add(new Parameter("user_id", userId));
		parameters.add(new Parameter("api_sig", AuthUtilities.getSignature(sharedSecret, parameters)));
		parameters.add(new Parameter("auth_token", RequestContext.getRequestContext().getAuth().getToken()));

		String key = "PeopleInterface_" + parameters.hashCode();
		Response response = this.flickrCache.get(key);

		if(response == null) {
			response = transportAPI.get(transportAPI.getPath(), parameters);
			this.flickrCache.set(key, response);
		}

		if (response.isError()) {
			throw new FlickrException(response.getErrorCode(), response.getErrorMessage());
		}
		Element userElement = response.getPayload();
		User user = new User();
		user.setId(userElement.getAttribute("nsid"));
		user.setAdmin("1".equals(userElement.getAttribute("isadmin")));
		user.setPro("1".equals(userElement.getAttribute("ispro")));
		user.setIconFarm(userElement.getAttribute("iconfarm"));
		user.setIconServer(userElement.getAttribute("iconserver"));
		
		user.setFamily("1".equals(userElement.getAttribute("family")));
		user.setFriend("1".equals(userElement.getAttribute("friend")));
		user.setContact("1".equals(userElement.getAttribute("contact")));
		user.setGender(Gender.fromString(userElement.getAttribute("gender")));
		user.setIgnored("1".equals(userElement.getAttribute("ignored")));
		
		user.setUsername(XMLUtilities.getChildValue(userElement, "username"));
		user.setRealName(XMLUtilities.getChildValue(userElement, "realname"));
		user.setLocation(XMLUtilities.getChildValue(userElement, "location"));
		user.setMbox_sha1sum(XMLUtilities.getChildValue(userElement, "mbox_sha1sum"));

		Element photosElement = XMLUtilities.getChild(userElement, "photos");
		user.setPhotosFirstDate(XMLUtilities.getChildValue(photosElement, "firstdate"));
		user.setPhotosFirstDateTaken(XMLUtilities.getChildValue(photosElement, "firstdatetaken"));
		user.setPhotosCount(XMLUtilities.getChildValue(photosElement, "count"));

		return user;
	}
	
	/**
	 * This method requires authentication with 'read' permission.
	 * 
	 * @param userId
	 * @param perPage
	 * @param page
	 * @param extras
	 * @param safeSearch
	 * <ul>
	 * <li>1 for safe</li>
     * <li>2 for moderate</li>
     * <li>3 for restricted</li>
     * </ul>
     * Set to 0 to not specify a privacy Filter.
     * 
	 * @param minUploadDate Minimum upload date. Photos with an upload date greater than or equal to this value will be returned. Set to null to not specify a date.
	 * @param maxUploadDate Maximum upload date. Photos with an upload date less than or equal to this value will be returned. Set to null to not specify a date.
	 * @param minTakenDate Minimum taken date. Photos with an taken date greater than or equal to this value will be returned. Set to null to not specify a date.
	 * @param maxTakenDate Maximum taken date. Photos with an taken date less than or equal to this value will be returned. Set to null to not specify a date.
	 * @param contentType
	 * <ul>
	 * <li>1 for photos only.</li>
     * <li>2 for screenshots only.</li>
     * <li>3 for 'other' only.</li>
     * <li>4 for photos and screenshots.</li>
     * <li>5 for screenshots and 'other'.</li>
     * <li>6 for photos and 'other'.</li>
     * <li>7 for photos, screenshots, and 'other' (all).</li>
     * </ul>
     * Set to 0 to not specify a content type filter.
     *  
	 * @param privacyFilter Return photos only matching a certain privacy level. Valid values are:
	 * <ul>
	 * <li>1 public photos</li>
	 * <li>2 private photos visible to friends</li>
	 * <li>3 private photos visible to family</li>
	 * <li>4 private photos visible to friends & family</li>
	 * <li>5 completely private photos</li>
	 * </ul>
	 * Set to 0 to not specify a privacy filter.
	 *
	 * @see com.aetrion.flickr.photos.Extras
	 * @return
	 */
	public PhotoList getPhotos(String userId, int perPage, int page, Set extras, int safeSearch,
			Date minUploadDate, Date maxUploadDate, Date minTakenDate, Date maxTakenDate, 
			int contentType, int privacyFilter) 
	throws IOException, SAXException, FlickrException {
		PhotoList photos = new PhotoList();

		List parameters = new ArrayList();
		parameters.add(new Parameter("method", METHOD_GET_PHOTOS));
		parameters.add(new Parameter("api_key", apiKey));

		parameters.add(new Parameter("user_id", userId));

		if (perPage > 0) {
			parameters.add(new Parameter("per_page", "" + perPage));
		}
		if (page > 0) {
			parameters.add(new Parameter("page", "" + page));
		}
		if (extras != null) {
			parameters.add(new Parameter(Extras.KEY_EXTRAS, StringUtilities.join(extras, ",")));
		}
		if(safeSearch > 0) {
			parameters.add(new Parameter("safe_search", "" + safeSearch));
		}
		if (minUploadDate != null) {
			parameters.add(new Parameter("min_upload_date", minUploadDate.getTime() / 1000L));
		}
		if (maxUploadDate != null) {
			parameters.add(new Parameter("max_upload_date", maxUploadDate.getTime() / 1000L));
		}
		if (minTakenDate != null) {
			parameters.add(new Parameter("min_taken_date", minTakenDate.getTime() / 1000L));
		}
		if (maxTakenDate != null) {
			parameters.add(new Parameter("max_taken_date", maxTakenDate.getTime() / 1000L));
		}
		if(contentType > 0) {
			parameters.add(new Parameter("content_type", contentType));
		}
		if (privacyFilter > 0) {
			parameters.add(new Parameter("privacy_filter", privacyFilter));
		}

		// This needs to be after everything else is it into parameters.
		parameters.add(
				new Parameter(
						"api_sig",
						AuthUtilities.getSignature(sharedSecret, parameters)
				)
		);

		String key = "PeopleInterface_" + parameters.hashCode();
		Response response = this.flickrCache.get(key);

		if(response == null) {
			response = transportAPI.get(transportAPI.getPath(), parameters);
			this.flickrCache.set(key, response);
		}

		if (response.isError()) {
			throw new FlickrException(response.getErrorCode(), response.getErrorMessage());
		}
		Element photosElement = response.getPayload();
		photos.setPage(photosElement.getAttribute("page"));
		photos.setPages(photosElement.getAttribute("pages"));
		photos.setPerPage(photosElement.getAttribute("perpage"));
		photos.setTotal(photosElement.getAttribute("total"));

		NodeList photoNodes = photosElement.getElementsByTagName("photo");
		for (int i = 0; i < photoNodes.getLength(); i++) {
			Element photoElement = (Element) photoNodes.item(i);
			photos.add(PhotoUtils.createPhoto(photoElement));
		}
		return photos;
	}
	
	/**
	 * Simplified version of getPhotos.
	 * 
	 * @see getPhotos()
	 * 
	 * @param userId
	 * @param perPage
	 * @param page
	 * @param extras
	 * @return
	 */
	public PhotoList getPhotos(String userId, int perPage, int page, Set extras) 
	throws IOException, SAXException, FlickrException {
		return getPhotos(userId, perPage, page, extras, 0, null, null, null, null, 0, 0);
	}

	/**
	 * Get a collection of public groups for the user.
	 *
	 * This method does not require authentication.
	 *
	 * @param userId The user ID
	 * @return The public groups
	 * @throws IOException
	 * @throws SAXException
	 * @throws FlickrException
	 */
	public Collection getPublicGroups(String userId)
	throws IOException, SAXException, FlickrException {
		List groups = new ArrayList();

		List parameters = new ArrayList();
		parameters.add(new Parameter("method", METHOD_GET_PUBLIC_GROUPS));
		parameters.add(new Parameter("api_key", apiKey));
		parameters.add(new Parameter("user_id", userId));

		// This needs to be after everything else is it into parameters.
		parameters.add(
				new Parameter(
						"api_sig",
						AuthUtilities.getSignature(sharedSecret, parameters)
				)
		);

		String key = "PeopleInterface_" + parameters.hashCode();
		Response response = this.flickrCache.get(key);

		if(response == null) {
			response = transportAPI.get(transportAPI.getPath(), parameters);
			this.flickrCache.set(key, response);
		}

		if (response.isError()) {
			throw new FlickrException(response.getErrorCode(), response.getErrorMessage());
		}
		Element groupsElement = response.getPayload();
		NodeList groupNodes = groupsElement.getElementsByTagName("group");
		for (int i = 0; i < groupNodes.getLength(); i++) {
			Element groupElement = (Element) groupNodes.item(i);
			Group group = new Group();
			group.setId(groupElement.getAttribute("nsid"));
			group.setName(groupElement.getAttribute("name"));
			group.setAdmin("1".equals(groupElement.getAttribute("admin")));
			group.setEighteenPlus("1".equals(groupElement.getAttribute("eighteenplus")));
			groups.add(group);
		}
		return groups;
	}

	/**
	 * 
	 * 
	 * @param userId
	 * @param perPage
	 * @param page
	 * @return
	 * @throws IOException
	 * @throws SAXException
	 * @throws FlickrException
	 */
	public PhotoList getPublicPhotos(String userId, int perPage, int page)
	throws IOException, SAXException, FlickrException {
		return getPublicPhotos(userId, Extras.MIN_EXTRAS, perPage, page);
	}

	/**
	 * Get a collection of public photos for the specified user ID.
	 *
	 * This method does not require authentication.
	 *
	 * @see com.aetrion.flickr.photos.Extras
	 * @param userId The User ID
	 * @param extras Set of extra-attributes to include (may be null)
	 * @param perPage The number of photos per page
	 * @param page The page offset
	 * @return The PhotoList collection
	 * @throws IOException
	 * @throws SAXException
	 * @throws FlickrException
	 */
	public PhotoList getPublicPhotos(String userId, Set extras, int perPage, int page) throws IOException, SAXException,
	FlickrException {
		PhotoList photos = new PhotoList();

		List parameters = new ArrayList();
		parameters.add(new Parameter("method", METHOD_GET_PUBLIC_PHOTOS));
		parameters.add(new Parameter("api_key", apiKey));

		parameters.add(new Parameter("user_id", userId));

		if (perPage > 0) {
			parameters.add(new Parameter("per_page", "" + perPage));
		}
		if (page > 0) {
			parameters.add(new Parameter("page", "" + page));
		}
		if (extras != null) {
			parameters.add(new Parameter(Extras.KEY_EXTRAS, StringUtilities.join(extras, ",")));
		}

		// This needs to be after everything else is it into parameters.
		parameters.add(
				new Parameter(
						"api_sig",
						AuthUtilities.getSignature(sharedSecret, parameters)
				)
		);

		String key = "PeopleInterface_" + parameters.hashCode();
		Response response = this.flickrCache.get(key);

		if(response == null) {
			response = transportAPI.get(transportAPI.getPath(), parameters);
			this.flickrCache.set(key, response);
		}

		if (response.isError()) {
			throw new FlickrException(response.getErrorCode(), response.getErrorMessage());
		}
		Element photosElement = response.getPayload();
		photos.setPage(photosElement.getAttribute("page"));
		photos.setPages(photosElement.getAttribute("pages"));
		photos.setPerPage(photosElement.getAttribute("perpage"));
		photos.setTotal(photosElement.getAttribute("total"));

		NodeList photoNodes = photosElement.getElementsByTagName("photo");
		for (int i = 0; i < photoNodes.getLength(); i++) {
			Element photoElement = (Element) photoNodes.item(i);
			photos.add(PhotoUtils.createPhoto(photoElement));
		}
		return photos;
	}

	/**
	 * Get upload status for the currently authenticated user.
	 *
	 * Requires authentication with 'read' permission using the new authentication API.
	 *
	 * @return A User object with upload status data fields filled
	 * @throws IOException
	 * @throws SAXException
	 * @throws FlickrException
	 */
	public User getUploadStatus() throws IOException, SAXException, FlickrException {
		List parameters = new ArrayList();
		parameters.add(new Parameter("method", METHOD_GET_UPLOAD_STATUS));
		parameters.add(new Parameter("api_key", apiKey));
		parameters.add(
				new Parameter(
						"api_sig",
						AuthUtilities.getSignature(sharedSecret, parameters)
				)
		);

		Response response = transportAPI.get(transportAPI.getPath(), parameters);
		if (response.isError()) {
			throw new FlickrException(response.getErrorCode(), response.getErrorMessage());
		}
		Element userElement = response.getPayload();
		User user = new User();
		user.setId(userElement.getAttribute("id"));
		user.setPro("1".equals(userElement.getAttribute("ispro")));
		user.setUsername(XMLUtilities.getChildValue(userElement, "username"));

		Element bandwidthElement = XMLUtilities.getChild(userElement, "bandwidth");
		user.setBandwidthMax(bandwidthElement.getAttribute("max"));
		user.setBandwidthUsed(bandwidthElement.getAttribute("used"));

		Element filesizeElement = XMLUtilities.getChild(userElement, "filesize");
		user.setFilesizeMax(filesizeElement.getAttribute("max"));

		return user;
	}
}
