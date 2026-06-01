package us.muit.fs.samples.auditserver.controllers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import us.muit.fs.a4i.model.entities.Metric;
import us.muit.fs.a4i.model.remote.GitHubRepositoryEnquirer;
import us.muit.fs.a4i.model.remote.RemoteEnquirer;
import us.muit.fs.samples.auditserver.config.AppProperties;

@RestController
public class HealthController {

    @Autowired
    private AppProperties config;

    private static Logger log = Logger.getLogger(HealthController.class.getName());

    private String getHealthzGithubRepo() {
        return config.getHealthzGithubRepo();
    }

    @GetMapping(path = "/readyz", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> healthz() {

        Map<String, Object> body = new HashMap<>();
        String healthzGithubRepo = this.getHealthzGithubRepo();

        body.put("remoteRepo", healthzGithubRepo);
        body.put("metric", "totalAdditions");
        body.put("totalAdditions", 0);

        try {
            String token = System.getenv("GITHUB_OAUTH");

            if (token == null || token.isBlank()) {
                token = System.getenv("GITHUB_TOKEN");
            }

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/" + healthzGithubRepo))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .header("Accept", "application/vnd.github+json");

            if (token != null && !token.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = client.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                body.put("healthy", true);

                try {
                    RemoteEnquirer remote = new GitHubRepositoryEnquirer();
                    Metric myMetric = remote.getMetric("totalAdditions", healthzGithubRepo);

                    if (myMetric != null) {
                        body.put("metric", myMetric);
                        body.put("totalAdditions", myMetric.getValue());
                    }
                } catch (Exception metricException) {
                    log.fine("No se ha podido recuperar totalAdditions, pero el repositorio es accesible: "
                            + metricException.getMessage());
                }

                return ResponseEntity.status(HttpStatus.OK).body(body);
            }

            body.put("healthy", false);
            body.put("error", "GitHub respondió con código " + response.statusCode());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);

        } catch (Exception ref) {
            log.fine("Se ha recibido esta excepción: " + ref);
            body.put("healthy", false);
            body.put("error", ref.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }
}