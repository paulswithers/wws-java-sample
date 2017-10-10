package org.opencode4workspace.javasamples;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opencode4workspace.WWClient;
import org.opencode4workspace.WWException;
import org.opencode4workspace.bo.Space;
import org.opencode4workspace.bo.WWQueryResponseObjectInterface;
import org.opencode4workspace.bo.WWQueryResponseObjectTypes;
import org.opencode4workspace.bo.Person;
import org.opencode4workspace.bo.Person.PersonFields;
import org.opencode4workspace.builders.BaseGraphQLMultiQuery;
import org.opencode4workspace.builders.ObjectDataSenderBuilder;
import org.opencode4workspace.builders.PersonGraphQLQuery.PersonAttributes;
import org.opencode4workspace.builders.SpaceGraphQLQuery.SpaceAttributes;
import org.opencode4workspace.builders.SpaceUpdateGraphQLMutation.UpdateSpaceMemberOperation;
import org.opencode4workspace.endpoints.WWAuthenticationEndpoint;
import org.opencode4workspace.endpoints.WWGraphQLEndpoint;
import org.opencode4workspace.graphql.DataContainer;
import org.opencode4workspace.graphql.GraphResultContainer;
import org.opencode4workspace.graphql.SpaceWrapper;
import org.opencode4workspace.graphql.UpdateSpaceContainer;
import org.opencode4workspace.json.GraphQLRequest;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class MainDemo extends AbstractVerticle {

	private static final int listenport = 8081;
	// We'll create a single user-authenticated WWClient for the user across the instance.
	// In real world, we would run oAuth dance as user and store details for the session or in a persistent store
	// To do that we need a user token. Retrieve by putting a URL in a browser, inserting redirectUri AND appId
	// https://api.watsonwork.ibm.com/oauth/authorize?client_id=MY_APP_ID&state=3322&scope=ibmid&redirect_uri=REDIRECT_URI&response_type=code
	// After allowing access, extract the "code" query parameter and insert below in userToken.
	// You will need a new one each time you start the verticle.
	private static WWClient userClient;
	private static String userToken = "";
	private static String redirectUri = "www.openntf.org";
	private static String appId = "";
	private static String appSecret = "";

	public static void main(String[] args) throws IOException, WWException {
		new MainDemo();
		int quit = 0;
		while (quit != 113) {
			System.out.println("Press q<Enter> to stop the verticle");
			quit = System.in.read();
		}
		System.out.println("Verticle terminated");
		System.exit(0);
	}

	public MainDemo() throws UnsupportedEncodingException, WWException {
		Vertx vertx = Vertx.factory.vertx();
		HttpServerOptions options = new HttpServerOptions();
		options.setPort(listenport);
		
		Router router = Router.router(vertx);
		
		router.route().handler(BodyHandler.create());
		
		router.post("/workspace").consumes("application.json").produces("application/json").handler(this::runQueryDefensive);
		
		vertx.createHttpServer(options).requestHandler(router::accept).listen();
	}
	
	public Map<String, Object> validateContent(JsonObject obj) {
		Map<String, Object> vals = new HashMap<>();
		List<String> mand = new ArrayList<String>();
		mand.add("spaceId");
		mand.add("emails");
		for (String label : mand) {
			if (!obj.containsKey(label)) {
				vals.put(label, label + " must be supplied");
			}
		}
		return vals;
	}
	
	public void runQueryDefensive(RoutingContext rc) {
		HttpServerResponse resp = rc.response();
		resp.putHeader("content-type", "application.json");
				
		JsonObject obj = rc.getBodyAsJson();
		Map errors = validateContent(obj);
		if (!errors.isEmpty()) {
			resp.setStatusCode(400);
			resp.end(Json.encodePrettily(errors));
		} else {
			try {
				// Authenticate as app, see https://wiki.openntf.org/display/WWSJava/Running+as+an+Application
				// In the real world, appSecret / appId would be passed in or stored as configuration of our Java application
				WWClient client = WWClient.buildClientApplicationAccess(appId, appSecret, new WWAuthenticationEndpoint());
				client.authenticate();
				if (null == userClient) {
					// Authenticate as user, see https://wiki.openntf.org/display/WWSJava/Running+as+a+User
					// Some endpoints, like getting people by email require user-level authority
					WWClient userClient = WWClient.buildClientUserAccess(userToken, appId, appSecret, new WWAuthenticationEndpoint(), redirectUri);
					userClient.authenticate();
				}
				
				String spaceId = obj.getString("spaceId");
				Space space = client.getSpaceById(spaceId);
				if (null == space) {
					resp.setStatusCode(400);
					errors.put("error", "The app you passed does not have access to the space you passed");
					resp.end(Json.encodePrettily(errors));
				} else {
					// Get users
					JsonArray emails = obj.getJsonArray("emails");
					List<ObjectDataSenderBuilder> builders = new ArrayList();
					for (Object email : emails) {
						ObjectDataSenderBuilder query = new ObjectDataSenderBuilder();
						query.setReturnType(WWQueryResponseObjectTypes.PERSON);
						query.setObjectName((String) email);
						query.addAttribute(PersonAttributes.EMAIL, email);
						query.addField(PersonFields.ID);
						builders.add(query);
					}
					BaseGraphQLMultiQuery multiPersonQuery = new BaseGraphQLMultiQuery("getPeople", builders);
					WWGraphQLEndpoint ep = new WWGraphQLEndpoint(userClient);
					GraphResultContainer resultContainer = userClient.getCustomQuery(multiPersonQuery);
					
					// Capture any errors
					if (!resultContainer.getErrors().isEmpty()) {
						errors.put("error", resultContainer.getErrors());
					}
					
					// Parse out user ids for all emails - we aliased them with the email
					ArrayList<String> personIds = new ArrayList<String>();
					DataContainer data = resultContainer.getData();
					if (null != data) {
						for (Object email : emails) {
							Person person = (Person) data.getAliasedChildren().get(email);
							personIds.add(person.getId());
						}
						
						// Now we have the IDs, we can update the space with access for them
						ArrayList<String> newMembers = ep.updateSpaceMembers(spaceId, personIds, UpdateSpaceMemberOperation.ADD);
						errors.put("usersAdded", newMembers);
						resp.end(Json.encodePrettily(errors));
					}
				}
			} catch (Exception e) {
				resp.setStatusCode(400);
				errors.put("error", e.getMessage());
				e.printStackTrace();
				resp.end(Json.encodePrettily(errors));
			}
		}
	}

}
