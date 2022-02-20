package dk.anno1980;

import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.SocketUtils;
import org.springframework.web.client.ResourceAccessException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class AppTest {

  @LocalServerPort private int port;

  private int proxyPort = 20001;

  @TestConfiguration
  static class TestConfig {
    @Bean
    RestTemplateBuilder restTemplateBuilder() {
      return new RestTemplateBuilder().setReadTimeout(Duration.of(5, ChronoUnit.SECONDS));
    }
  }

  @Autowired private TestRestTemplate restTemplate;

  @Container
  private GenericContainer<?> toxiproxy =
      new GenericContainer<>(
              DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.3.0")
                  .asCompatibleSubstituteFor("shopify/toxiproxy"))
          .withExposedPorts(8474, proxyPort);

  @Test
  public void proxyOnlyWorks() throws IOException {
    ToxiproxyClient toxiproxyClient =
        new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getFirstMappedPort());

    InetAddress hostRunningTheAppEndpoint = InetAddress.getLocalHost();

    toxiproxyClient.createProxy(
        "my-proxy",
        "0.0.0.0:" + proxyPort,
        hostRunningTheAppEndpoint.getHostAddress() + ":" + port);

    ResponseEntity<String> stringResponseEntity =
        this.restTemplate.getForEntity(
            "http://localhost:" + toxiproxy.getMappedPort(proxyPort) + "/", String.class);
    assertEquals(HttpStatus.OK, stringResponseEntity.getStatusCode());
  }

  @Test
  public void proxyWithLatency() throws IOException {
    ToxiproxyClient client =
        new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getFirstMappedPort());

    InetAddress hostRunningTheAppEndpoint = InetAddress.getLocalHost();

    Proxy proxy =
        client.createProxy(
            "my-proxy", "0.0.0.0:" + proxyPort, hostRunningTheAppEndpoint + ":" + port);
    proxy.toxics().latency("latency", ToxicDirection.DOWNSTREAM, 4_000);

    ResponseEntity<String> stringResponseEntity =
        this.restTemplate.getForEntity(
            "http://localhost:" + toxiproxy.getMappedPort(proxyPort) + "/", String.class);
    assertEquals(HttpStatus.OK, stringResponseEntity.getStatusCode());
  }

  @Test
  public void proxyWithLatencyTimeout() throws IOException {
    ToxiproxyClient client =
        new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getFirstMappedPort());

    InetAddress hostRunningTheAppEndpoint = InetAddress.getLocalHost();

    Proxy proxy =
        client.createProxy(
            "my-proxy", "0.0.0.0:" + proxyPort, hostRunningTheAppEndpoint + ":" + port);
    proxy.toxics().latency("latency", ToxicDirection.DOWNSTREAM, 6_000);

    ResourceAccessException resourceAccessException =
        assertThrows(
            ResourceAccessException.class,
            () ->
                this.restTemplate.getForEntity(
                    "http://localhost:" + toxiproxy.getMappedPort(proxyPort) + "/", String.class));

    assertEquals(SocketTimeoutException.class, resourceAccessException.getRootCause().getClass());
  }

  @Test
  public void worksNoProxy() {
    ResponseEntity<String> stringResponseEntity =
        this.restTemplate.getForEntity("http://localhost:" + port + "/", String.class);
    assertEquals(HttpStatus.OK, stringResponseEntity.getStatusCode());
  }
}
