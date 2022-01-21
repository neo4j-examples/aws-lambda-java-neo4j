package example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.util.Collections;
import java.util.HashMap;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.BufferedWriter;
import java.nio.charset.Charset;
import java.lang.IllegalStateException;
import java.util.Map;

import org.neo4j.driver.*;
import org.neo4j.driver.exceptions.Neo4jException;

// Handler value: example.HandlerStream
public class HandlerStream implements RequestStreamHandler {
  Gson gson = new GsonBuilder().setPrettyPrinting().create();
  String uri = "<Uri for Neo4j Aura database>";
  String user = "neo4j";
  String password = "<Password for Neo4j Aura database>";
  private final Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password), Config.defaultConfig());

  @Override
  public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
    LambdaLogger logger = context.getLogger();
    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, Charset.forName("US-ASCII")));
    PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outputStream, Charset.forName("US-ASCII"))));

    try {
      HashMap event = gson.fromJson(reader, HashMap.class);
      logger.log("STREAM TYPE: " + inputStream.getClass().toString());
      logger.log("EVENT TYPE: " + event.getClass().toString());
      logger.log("EVENT value: " + event.get("name").toString());

      String readPersonByNameQuery = "MATCH (p:Person)\n" +
              "WHERE p.name = $person_name\n" +
              "RETURN p.name AS name";
      Map<String, Object> params = Collections.singletonMap("person_name", event.get("name").toString());

      try (Session session = driver.session()) {
        Record record = session.readTransaction(tx -> {
          Result result = tx.run(readPersonByNameQuery, params);
          return result.single();
        });
        writer.write(gson.toJson(record.asMap()));
        if (writer.checkError()) {
          logger.log("WARNING: Writer encountered an error.");
        }
        logger.log(String.format("Found person: %s", record.get("name").asString()));
      } catch (Neo4jException ex) {
        logger.log("Query raised an exception" + ex);
        throw ex;
      }
    } catch (IllegalStateException | JsonSyntaxException exception) {
      logger.log(exception.toString());
    } finally {
      reader.close();
      writer.close();
    }
  }

}