package org.opencode4workspace.xpages;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;

import org.opencode4workspace.WWClient;
import org.opencode4workspace.bo.MessageResponse;
import org.opencode4workspace.bo.Person;
import org.opencode4workspace.bo.Space;
import org.opencode4workspace.bo.WWQueryResponseObjectTypes;
import org.opencode4workspace.bo.Person.PersonFields;
import org.opencode4workspace.bo.Space.SpaceFields;
import org.opencode4workspace.builders.AppMessageBuilder;
import org.opencode4workspace.builders.BaseGraphQLMultiQuery;
import org.opencode4workspace.builders.ObjectDataSenderBuilder;
import org.opencode4workspace.builders.SpacesGraphQLQuery;
import org.opencode4workspace.builders.PersonGraphQLQuery.PersonAttributes;
import org.opencode4workspace.builders.SpaceUpdateGraphQLMutation.UpdateSpaceMemberOperation;
import org.opencode4workspace.endpoints.AppMessage;
import org.opencode4workspace.endpoints.WWAuthenticationEndpoint;
import org.opencode4workspace.endpoints.WWGraphQLEndpoint;
import org.opencode4workspace.graphql.DataContainer;
import org.opencode4workspace.graphql.ErrorContainer;
import org.opencode4workspace.graphql.GraphResultContainer;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.designer.context.XSPUrl;
import com.ibm.xsp.extlib.util.ExtLibUtil;

public class SessionBean implements Serializable {
	private static final long serialVersionUID = 1L;
	private boolean authenticated;
	private String appId;
	private String appSecret;
	private WWClient userClient;
	private WWClient appClient;
	private Map<String, String> spaces;
	private String selectedSpace;
	private Person me;

	public SessionBean() {

	}

	public void loadAppSettings() {
		String appIdInt = ExtLibUtil.getXspProperty("wws.appId");
		String appSecretInt = ExtLibUtil.getXspProperty("wws.appSecret");
		if (StringUtil.isEmpty(appIdInt) || StringUtil.isEmpty(appSecretInt)) {
			addError("Application not linked to a Watson Workspace app. Update Xsp Properties and rebuild");
		}
		setAppId(appIdInt);
		setAppSecret(appSecretInt);
		try {
			setAppClient(WWClient.buildClientApplicationAccess(appId, appSecret, new WWAuthenticationEndpoint()));
			getAppClient().authenticate();
			if (!getAppClient().isAuthenticated()) {
				addError("Could not authenticate application!");
			}
		} catch (Throwable t) {
			addError(t.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	public void checkAuthenticated() {
		Map param = (Map) ExtLibUtil.resolveVariable("param");
		if (param.containsKey("code")) {
			try {
				if (null == userClient) {
					userClient = WWClient.buildClientUserAccess((String) param.get("code"), getAppId(), getAppSecret(),
							new WWAuthenticationEndpoint(), getCurrUrl());
					userClient.authenticate();
					setMe(userClient.getMe());
					setAuthenticated(true);
				}
				loadSpaces();
				addConfirmation("Authenticated as user " + getMe().getDisplayName());
				addConfirmation("Loaded the list of Spaces this app has access to");
			} catch (Throwable t) {
				addError(t.getMessage());
				userClient = null;
			}
		}
	}

	public void loadSpaces() {
		try {
			Map<String, String> spaces = new HashMap<String, String>();
			SpacesGraphQLQuery query = new SpacesGraphQLQuery();
			query.addField(SpaceFields.ID).addField(SpaceFields.TITLE);
			List<? extends Space> spacesResult = appClient.getSpacesWithQuery(query);
			for (Space space : spacesResult) {
				spaces.put(space.getTitle(), space.getId());
			}
			setSpaces(spaces);
		} catch (Throwable t) {
			addError("Loading Spaces: " + t.getMessage());
			t.printStackTrace();
		}
	}

	public void addUsersToSpace() {
		updateSpace(UpdateSpaceMemberOperation.ADD);
	}

	public void removeUsersFromSpace() {
		updateSpace(UpdateSpaceMemberOperation.REMOVE);
	}

	public Map<String, String> getUserIds() {
		Map<String, String> personIds = new HashMap<String, String>();
		try {
			int x = 0;
			BaseGraphQLMultiQuery multiPersonQuery = null;
			for (Object email : getSelectedEmails()) {
				ObjectDataSenderBuilder query = new ObjectDataSenderBuilder();
				query.setReturnType(WWQueryResponseObjectTypes.PERSON);
				query.setObjectName("elem" + Integer.toString(x));
				query.addAttribute(PersonAttributes.EMAIL, email).addField(PersonFields.ID).addField(
						PersonFields.DISPLAY_NAME);
				// This demo highlighted a bug in released version for passing an ObjectDataSenderBuilder list. Instead this is needed
				if (null == multiPersonQuery) {
					multiPersonQuery = new BaseGraphQLMultiQuery("getPeople", query);
				} else {
					multiPersonQuery.addQueryObject(query);
				}
				x++;
			}

			GraphResultContainer resultContainer = userClient.getCustomQuery(multiPersonQuery);

			if (null != resultContainer.getErrors()) {
				for (ErrorContainer error : resultContainer.getErrors()) {
					addError(error.getMessage());
				}
			}

			DataContainer data = resultContainer.getData();
			if (null != data) {
				for (x = 0; x < getSelectedEmails().size(); x++) {
					Map<String, Object> people = data.getAliasedChildren();
					if (people.containsKey("elem" + Integer.toString(x))) {
						Person person = (Person) data.getAliasedChildren().get("elem" + Integer.toString(x));
						personIds.put(person.getId(), person.getDisplayName());
					}
				}
			}
		} catch (Throwable t) {
			addError("Getting userIds: " + t.getMessage());
			t.printStackTrace();
		}
		return personIds;
	}

	private void updateSpace(UpdateSpaceMemberOperation addOrRemove) {
		try {
			if (getSelectedEmails().isEmpty()) {
				addError("Please select at least one contact");
				return;
			}
			Map<String, String> people = getUserIds();
			if (!people.isEmpty()) {
				WWGraphQLEndpoint ep = new WWGraphQLEndpoint(userClient);
				List<String> userIds = new ArrayList<String>(people.keySet());
				String addOrRemoveMessage = "Added";
				ArrayList<String> amendedMembers = ep.updateSpaceMembers(getSelectedSpace(), userIds, addOrRemove);
				if (addOrRemove.equals(UpdateSpaceMemberOperation.REMOVE)) {
					addOrRemoveMessage = "Removed";
				}
				addConfirmation("Contacts " + addOrRemoveMessage);
				for (String member : amendedMembers) {
					addConfirmation(addOrRemoveMessage + " " + people.get(member));
				}
			}
		} catch (Throwable t) {
			addError(t.getMessage());
			t.printStackTrace();
		}
	}

	public void postMessageToSpace() {
		try {
			String message = (String) ExtLibUtil.getViewScope().get("message");
			if (StringUtil.isEmpty(message)) {
				addError("Please enter a message");
				return;
			}
			AppMessage msg = new AppMessageBuilder().setColor("#FF0000").setMessage(message).build();
			MessageResponse response = userClient.postMessageToSpace(msg, getSelectedSpace());
			addConfirmation("Posted message " + response.getId());
		} catch (Throwable t) {
			addError(t.getMessage());
			t.printStackTrace();
		}
	}

	public void deleteSpace() {
		try {
			if (userClient.deleteSpace(getSelectedSpace())) {
				addConfirmation("Space deleted");
				loadSpaces();
			}
		} catch (Throwable t) {
			addError(t.getMessage());
			t.printStackTrace();
		}
	}

	public void addSpace() {
		try {
			Space newSpace = userClient.createSpace((String) ExtLibUtil.getRequestScope().get("spaceName"));
			addConfirmation("Space created. Please add the app into access to the space and click \"Reload Spaces\"");
		} catch (Throwable t) {
			addError(t.getMessage());
			t.printStackTrace();
		}
	}

	public void setAuthenticated(boolean authenticated) {
		this.authenticated = authenticated;
	}

	public boolean isAuthenticated() {
		return authenticated;
	}

	public String getAppId() {
		if (null == appId) {
			loadAppSettings();
		}
		return appId;
	}

	public void setAppId(String appId) {
		this.appId = appId;
	}

	public String getAppSecret() {
		return appSecret;
	}

	public void setAppSecret(String appSecret) {
		this.appSecret = appSecret;
	}

	public WWClient getUserClient() {
		return userClient;
	}

	public void setUserClient(WWClient userClient) {
		this.userClient = userClient;
	}

	public void setAppClient(WWClient appClient) {
		this.appClient = appClient;
	}

	public WWClient getAppClient() {
		return appClient;
	}

	public void setSpaces(Map<String, String> spaces) {
		this.spaces = spaces;
	}

	public Map<String, String> getSpaces() {
		return spaces;
	}

	public void setSelectedSpace(String selectedSpace) {
		this.selectedSpace = selectedSpace;
	}

	public String getSelectedSpace() {
		return selectedSpace;
	}

	public ArrayList<String> getSelectedEmails() {
		Object emails = ExtLibUtil.getViewScope().get("selectedEmails");
		ArrayList<String> retVal = new ArrayList<String>();
		if (emails instanceof Vector) {
			retVal.addAll((Vector<String>) emails);
		} else {
			retVal.add(emails.toString());
		}
		return retVal;
	}

	public void setMe(Person me) {
		this.me = me;
	}

	public Person getMe() {
		return me;
	}

	private void addError(String error) {
		FacesContext.getCurrentInstance().addMessage("", new FacesMessage(error));
	}

	private void addConfirmation(String message) {
		ArrayList<String> messages = new ArrayList<String>();
		if (ExtLibUtil.getRequestScope().containsKey("confirmationMessages")) {
			messages = (ArrayList<String>) ExtLibUtil.getRequestScope().get("confirmationMessages");
		}
		messages.add(message);
		ExtLibUtil.getRequestScope().put("confirmationMessages", messages);
	}

	public String getCurrUrl() {
		XSPUrl url = ExtLibUtil.getXspContext().getUrl();
		return url.getAddress();
	}
}
