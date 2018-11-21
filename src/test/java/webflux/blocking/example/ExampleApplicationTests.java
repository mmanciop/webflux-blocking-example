package webflux.blocking.example;

import com.google.common.base.Stopwatch;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofSeconds;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT,
				classes = ExampleApplication.class)
public class ExampleApplicationTests {

	@Autowired
	private WebTestClient webClient;

	@Test
	public void delayedGreetingsWithNoDelay() {
		testDelay(ofSeconds(0L));
	}

	@Test
	public void delayedGreetingsByOneSecond() {
		testDelay(ofSeconds(1L));
	}

	@Test
	public void delayedGreetingsByTwoSeconds() {
		testDelay(ofSeconds(2L));
	}

	private void testDelay(final Duration delay) {
		final Stopwatch stopwatch = Stopwatch.createStarted();

		webClient
				.get().uri(uriBuilder -> uriBuilder.path("/hello")
					.queryParam("delayInSeconds", delay.getSeconds())
					.build())
				.exchange()
				.expectStatus().isOk()
				.expectBody().consumeWith((res) ->
				assertThat(new String(res.getResponseBody(), UTF_8), is("Delayed greetings!")));

		stopwatch.stop();

		Assert.assertTrue(stopwatch.elapsed().getSeconds() >= delay.getSeconds());
	}

}
