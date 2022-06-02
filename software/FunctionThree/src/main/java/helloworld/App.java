package helloworld;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.neo4j.driver.Record;
import org.neo4j.driver.*;
import org.neo4j.driver.exceptions.Neo4jException;
import software.amazon.lambda.powertools.metrics.Metrics;
import software.amazon.lambda.powertools.tracing.Tracing;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static software.amazon.lambda.powertools.tracing.CaptureMode.DISABLED;

/**
 * Handler for requests to Lambda function.
 */
public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    String uri = "<Uri for Neo4j Aura database>";
    String user = "neo4j";
    String password = "<Password for Neo4j Aura database>";
    private final Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password), Config.defaultConfig());

    @Tracing(captureMode = DISABLED)
    @Metrics(captureColdStart = true)
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(headers);

        String readQuery = "MATCH (j:JavaVersion)\n" +
                "WHERE j.version = $version\n" +
                "RETURN j.version AS version, j.status AS status, j.gaDate AS ga, j.eolDate AS eol;";
        String version = "17";
        Map<String, Object> params = Collections.singletonMap("version", version);

        try {
            Session session = driver.session();
            Record record = session.readTransaction(tx -> {
                Result result = tx.run(readQuery, params);
                return result.single();
            });
            String output = String.format("{ \"results\": \"%s\" }", gson.toJson(record.asMap()));

            return response
                    .withStatusCode(200)
                    .withBody(output);
        } catch (Neo4jException e) {
            return response
                    .withBody("{}")
                    .withStatusCode(500);
        }
    }
}