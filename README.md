# wws-java-sample
Watson Workspace Java SDK Example

## Basic Vertx Demo

This comprises a basic Vertx verticle which can be called from Postman with an token, spaceId, list of email addresses and a message. It will add those users to the space.

### Configuring the Demo

Configuration is in vertx/src/main/conf/application-conf.json. Set the values you wish and save:

- **http.port**: localhost port to use
- **wws.redirectUri**: the redirect URI to use in the URL to get a token
- **wws.appId**: appId for the app you have connected to the relevant space
- **wws.appSecret**: appSecret for the app you have connected to the relevant space

### Running with Maven
1. Navigate to the vertx folder of the repo.
2. Run `mvn clean package`.
3. Run `java -jar target/Vertx-0.0.1-SNAPSHOT-fat.jar -conf src/main/resources/application-conf.json`

The verticle will launch.

Test by opening a browser and going to **localhost:8081/helloWorld** (assuming you've chosen port 8081 in the config).

### Running from Eclipse
1. Import as a Maven project
2. Right-click on org.opencode4workspace.javasamples.MainDemo and Run As > Application

### Running the demo
You will need a token for each time you run the demo. Open a browser and go to `https://api.watsonwork.ibm.com/oauth/authorize?client_id=MY_APP_ID&state=3322&scope=ibmid&redirect_uri=REDIRECT_URI&response_type=code`, adding in the appId and redirectUri you put in the configuration. When you log into Watson Workspace and Allow Watson Workspace to run on your behalf, it will redirect to the redirectUri with a token, e.g. "https://openntf.org/main.nsf?code=**t1XnpX**&state=3322". Copy the token.

1. In a REST Client (e.g. Postman) create a POST request to **localhost:8081/workspace** (assuming you've chosen port 8081 in the config).
2. Add a Header **Content_Type** set as `application/json`.
3. Set the Body with raw JSON comprising four elements:
	1. **token**: a string copied above, e.g. "3kLhAg"
	2. **spaceId**: a string, e.g. "5811aeb9e4b0052629e89bb2". You can find the spaceId by using the WWS Explorer
	3. **emails**: an array of emails, e.g. ["test1@myTest.com","test2@myTest.com"]
	4. **message**: a string message. To format a message, see https://help.workspace.ibm.com/hc/en-us/articles/229709367-Formatting-your-messages.

## XPages Sample
**NOTE**: To run the XPages app you will need to make changes to the Java security policy. The WWS Java SDK uses Java reflection to auto-convert JSON to Java objects. This is a restricted operation. To make it work, add the following to the "grant" block of your java.policy / java.pol file.

	permission java.lang.reflect.ReflectPermission "suppressAccessChecks";
	permission java.lang.RuntimePermission "accessDeclaredMembers";

**NOTE**: This has not been tested on Domino Designer local preview. It won't be tested on Domino Designer local preview. IBM have a **FREE** developer version available. Use it!

1. Download the XPages Packaged Plugin 1.1.0 from OpenNTF Watson Work Services Java SDK project on [OpenNTF](https://openntf.org/main.nsf/project.xsp?r=project/Watson Work Services Java SDK)
2. Install the plugin to Domino Designer
3. Install the plugin on the server
4. Create a new NSF from the ODP.
5. Add your appId and appSecret to the Xsp Properties.
6. Sign and clean the application.
7. Open the Demo XPage and have fun!