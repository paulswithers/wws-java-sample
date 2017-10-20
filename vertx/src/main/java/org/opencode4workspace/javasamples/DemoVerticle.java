package org.opencode4workspace.javasamples;

/*

<!--
Copyright 2017 Paul Withers
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and limitations under the License
-->

*/

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
import org.opencode4workspace.bo.MessageResponse;
import org.opencode4workspace.bo.Person;
import org.opencode4workspace.bo.Person.PersonFields;
import org.opencode4workspace.builders.AppMessageBuilder;
import org.opencode4workspace.builders.BaseGraphQLMultiQuery;
import org.opencode4workspace.builders.ObjectDataSenderBuilder;
import org.opencode4workspace.builders.PersonGraphQLQuery.PersonAttributes;
import org.opencode4workspace.builders.SpaceGraphQLQuery.SpaceAttributes;
import org.opencode4workspace.builders.SpaceUpdateGraphQLMutation.UpdateSpaceMemberOperation;
import org.opencode4workspace.endpoints.AppMessage;
import org.opencode4workspace.endpoints.WWAuthenticationEndpoint;
import org.opencode4workspace.endpoints.WWGraphQLEndpoint;
import org.opencode4workspace.graphql.DataContainer;
import org.opencode4workspace.graphql.GraphResultContainer;
import org.opencode4workspace.graphql.SpaceWrapper;
import org.opencode4workspace.graphql.UpdateSpaceContainer;
import org.opencode4workspace.json.GraphQLRequest;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
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

public class DemoVerticle extends AbstractVerticle {

	/*
	 * Authentication requires appId and appSecret. Please enter yours as static variables here. In practice, this would
	 * be in configuration.
	 * 
	 * User-level authentication is needed to find ids for users from email - it checks the people you have access to.
	 * To do that we need a user token, which will be passed into the REST service. Retrieve by putting a URL in a
	 * browser, inserting redirectUri AND appId.
	 * 
	 * https://api.watsonwork.ibm.com/oauth/authorize?client_id=MY_APP_ID&state=3322&scope=ibmid&redirect_uri=REDIRECT_URI&response_type=code
	 * https://api.watsonwork.ibm.com/oauth/authorize?client_id=156923ee-490e-49bb-986d-1e7b4f3759b4&state=3322&scope=ibmid&redirect_uri=https://openntf.org&response_type=code
	 * You will need a new one each time you make a request.
	 */

	public DemoVerticle() {

	}

	@Override
	public void start() throws Exception {
		HttpServerOptions options = new HttpServerOptions();
		options.setPort(config().getInteger("http.port", 8082));

		Router router = Router.router(vertx);

		router.route().handler(BodyHandler.create());

		// This is just a basic test - localhost:8081/helloWorld in a browser
		router.get("/helloWorld").handler(rc -> {
			HttpServerResponse resp = rc.response();
			resp.putHeader("content-type", "text/plain");
			resp.end("Hello World from Vert.x-Web!");
		});

		/*
		 * This is the main REST service. Use e.g. Postman going to localhost:8081/workspace doing a POST request.
		 * 
		 * Add header param for ContentType as application/json
		 * 
		 * Send JSON content with four elements - token, spaceId, emails, and message. e.g.
		 * {"token":"7tcuLr","spaceId":"5821aeb9f4b0052628e89bb2","emails":["pwithers@intec.co.uk"], "message":"Welcome from demo!"}
		 * 
		 * The golden rule of Vertx is don't block the event loop in a handler. 
		 * Calling WW will block - it needs to wait for the response. This is a basic workaround.
		 * 
		 */
		router.post("/workspace").consumes("application/json").produces("application/json")
				.blockingHandler(this::runQueryDefensive);

		vertx.createHttpServer(options).requestHandler(router::accept).listen();
	}

	/**
	 * REST service processing with defensive coding
	 * 
	 * @param rc RoutingContext from which to get request and response
	 */
	public void runQueryDefensive(RoutingContext rc) {
		// Return JSON
		HttpServerResponse resp = rc.response();
		resp.putHeader("content-type", "application/json");

		// Get POST data and validate
		JsonObject obj = rc.getBodyAsJson();
		Map errors = validateContent(obj);
		if (!errors.isEmpty()) {
			resp.setStatusCode(400);
			resp.end(Json.encodePrettily(errors));
		} else {
			try {
				/*
				 * Authenticate as app, see https://wiki.openntf.org/display/WWSJava/Running+as+an+Application.
				 * In the real world, appSecret / appId would be passed in or stored as configuration of our Java application
				 */
				WWClient client = WWClient.buildClientApplicationAccess(config().getString("wws.appId"), config().getString("wws.appSecret"),
						new WWAuthenticationEndpoint());
				client.authenticate();

				// Validate the app has access to this space
				String spaceId = obj.getString("spaceId");
				try {
					Space space = client.getSpaceById(spaceId);
				} catch (Exception e) {
					resp.setStatusCode(400);
					errors.put("error", "This app does not have access to the space you passed");
					resp.end(Json.encodePrettily(errors));
				}
				
				/*
				 * Get Person elements from WW for the emails passed - this needs to be run as a user.
				 * 
				 * query getPeople {
				 * 		elem0:person(email:"test1@intec.co.uk"){
				 * 			id
				 * 		}
				 * 		elem1:person(email:"test2@intec.co.uk"){
				 * 			id
				 * 		}
				 * }
				 * 
				 * This demonstrates passing multiple queries in a single request as well as aliases. 
				 * Because we're getting more than one Person object, we can't just retrieve them as "person".
				 * We need to pass an alias for each. But we can't use the email address for the alias, 
				 * it can only be a String of alphanumeric characters starting with an alpha character. 
				 */
				JsonArray emails = obj.getJsonArray("emails");
				int x = 0;
				BaseGraphQLMultiQuery multiPersonQuery = null;
				for (Object email : emails) {
					ObjectDataSenderBuilder query = new ObjectDataSenderBuilder();
					query.setReturnType(WWQueryResponseObjectTypes.PERSON);
					query.setObjectName("elem" + Integer.toString(x));
					query.addAttribute(PersonAttributes.EMAIL, email)
						.addField(PersonFields.ID);
					// This demo highlighted a bug in released version for passing an ObjectDataSenderBuilder list. Instead this is needed
					if (null == multiPersonQuery) {
						multiPersonQuery = new BaseGraphQLMultiQuery("getPeople", query);
					} else {
						multiPersonQuery.addQueryObject(query);
					}
					x++;
				}
				// Authenticate as a user https://wiki.openntf.org/display/WWSJava/Running+as+a+User
				// In real world, we would run oAuth dance as user and store details for the session or in a persistent store
				WWClient userClient = WWClient.buildClientUserAccess(obj.getString("token"), config().getString("wws.appId"), config().getString("wws.appSecret"),
						new WWAuthenticationEndpoint(), config().getString("wws.redirectUri"));
				userClient.authenticate();
				GraphResultContainer resultContainer = userClient.getCustomQuery(multiPersonQuery);

				// Capture any errors
				if (null != resultContainer.getErrors()) {
					errors.put("error", resultContainer.getErrors());
				}

				// Parse out user ids for all emails - we aliased them with "elem0", "elem1" etc
				ArrayList<String> personIds = new ArrayList<String>();
				DataContainer data = resultContainer.getData();		
				if (null != data) {
					Map<String, Object> people = data.getAliasedChildren();
					for (x = 0; x < emails.size(); x++) {
						if (people.containsKey("elem" + Integer.toString(x))) {
							Person person = (Person) data.getAliasedChildren().get("elem" + Integer.toString(x));
							personIds.add(person.getId());
						}
					}

					// Now we have the IDs, we can update the space with access for them. This can be done as the app
					WWGraphQLEndpoint ep = new WWGraphQLEndpoint(client);
					ArrayList<String> newMembers = ep.updateSpaceMembers(spaceId, personIds,
							UpdateSpaceMemberOperation.ADD);
					errors.put("usersAdded", newMembers);

					// We can also post a message, as the app
					AppMessageBuilder builder = new AppMessageBuilder();
					builder.setActorAvatar("http://gravatar.com/psw")
						.setActorName("PSW")
						.setActorUrl("http://www.intec.co.uk")
						.setColor("#FF0000")
						.setMessage(obj.getString("message"));
					AppMessage message = builder.build();
					MessageResponse response = client.postMessageToSpace(message, spaceId);
					errors.put("messageId", response.getId());
					resp.end(Json.encodePrettily(errors));
				}
			} catch (Exception e) {
				resp.setStatusCode(400);
				errors.put("error", e.getMessage());
				e.printStackTrace();
				resp.end(Json.encodePrettily(errors));
			}
		}
	}

	/**
	 * Validates all params have been completed
	 * 
	 * @param obj JSON passed into REST service
	 * @return Map of missing data
	 */
	public Map<String, Object> validateContent(JsonObject obj) {
		Map<String, Object> vals = new HashMap<>();
		List<String> mand = new ArrayList<String>();
		mand.add("spaceId");
		mand.add("emails");
		mand.add("token");
		mand.add("message");
		for (String label : mand) {
			if (!obj.containsKey(label)) {
				vals.put(label, label + " must be supplied");
			}
		}
		return vals;
	}

}
