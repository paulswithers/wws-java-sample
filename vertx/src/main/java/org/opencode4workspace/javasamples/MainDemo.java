package org.opencode4workspace.javasamples;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.opencode4workspace.WWException;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class MainDemo {

	public static void main(String[] args) throws IOException, WWException, URISyntaxException {
		DemoVerticle demo = new DemoVerticle();
		//Get file from resources folder
		ClassLoader classLoader = demo.getClass().getClassLoader();
		Path path = Paths.get(classLoader.getResource("application-conf.json").toURI());
		StringBuilder b = new StringBuilder();
		Files.lines(path, StandardCharsets.UTF_8).forEach(b::append);
		
		JsonObject obj = new JsonObject(b.toString());
		
		Vertx vertx = Vertx.factory.vertx();
		DeploymentOptions options = new DeploymentOptions();
		options.setConfig(obj);
		vertx.deployVerticle(demo, options);
		int quit = 0;
		while (quit != 113) {
			System.out.println("Press q<Enter> to stop the verticle");
			quit = System.in.read();
		}
		System.out.println("Verticle terminated");
		System.exit(0);
	}

}
