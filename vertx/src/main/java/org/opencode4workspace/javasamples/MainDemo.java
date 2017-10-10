package org.opencode4workspace.javasamples;

import java.io.IOException;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class MainDemo extends AbstractVerticle {

	private static final int listenport = 8081;

	public MainDemo() {
		Vertx vertx = Vertx.factory.vertx();
		HttpServerOptions options = new HttpServerOptions();
		options.setPort(listenport);
		
		Router router = Router.router(vertx);
		
		router.route().handler(BodyHandler.create());
		
		router.post("/workspace").consumes("application.json").produces("application/json").handler(rc -> {
			JsonObject obj = rc.getBodyAsJson();
		});
		
		vertx.createHttpServer(options).requestHandler(router::accept).listen();
	}

	public static void main(String[] args) throws IOException {
		new MainDemo();
		int quit = 0;
		while (quit != 113) {
			System.out.println("Press q<Enter> to stop the verticle");
			quit = System.in.read();
		}
		System.out.println("Verticle terminated");
		System.exit(0);
	}

}
