package webflux.blocking.example;

import static reactor.core.publisher.Mono.defer;
import static reactor.core.publisher.Mono.fromFuture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.config.EnableWebFlux;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.ipc.netty.http.HttpResources;

@EnableWebFlux
@SpringBootApplication
public class ExampleApplication {

	private static final String EXECUTOR_WORKER_THREAD_NAME_PREFIX = "executor-worker-thread-";

	@RestController
	public static class Resource {

		private static final Logger LOGGER = LoggerFactory.getLogger(Resource.class);

		private final static ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(10,
				new ThreadFactory() {
			final AtomicInteger counter = new AtomicInteger();

					@Override
					public Thread newThread(Runnable r) {
						final Thread t = new Thread(r);
						t.setDaemon(true);
						t.setName(EXECUTOR_WORKER_THREAD_NAME_PREFIX + counter.incrementAndGet());
						return t;
					}
				});

		private final static Scheduler REACTOR_HTTP_NIO_SCHEDULER =
				Schedulers.fromExecutor(HttpResources.get().onServer(true));

		@GetMapping("/hello")
		public Mono<String> greetBackAfterDelay(
				@RequestParam(name = "delayInSeconds", defaultValue = "0L")
				final long delayInSeconds) {
			/*
			 * Defer is important to delay the beginning of the wait until the subscription
			 * of this Mono, as CompletableFuture#runAsync starts to run the code immediately
			 */
			return defer(() -> fromFuture(CompletableFuture.runAsync(() -> {
				try {
					LOGGER.info("Time to go to sleep!");

					Thread.sleep(TimeUnit.SECONDS.toMillis(delayInSeconds));

					LOGGER.info("Time to wake up!");
				} catch (InterruptedException ex) {
					throw Exceptions.propagate(ex);
				}

			}, EXECUTOR_SERVICE)))
				.doOnNext(ignored -> {
					final String currentThreadName = Thread.currentThread().getName();

					Assert.isTrue(!Schedulers.isInNonBlockingThread(),
							"Logic should not be on a NonBlocking thread");
					Assert.isTrue(currentThreadName.startsWith("executor-worker-thread-"),
							"Logic should execute on the executor thread");
				})
				.publishOn(REACTOR_HTTP_NIO_SCHEDULER)
				.doOnNext(ignored -> Assert.isTrue(Schedulers.isInNonBlockingThread(),
						"Emission should take plane on a non-blocking thread"))
				.then(Mono.just("Delayed greetings!"));
		}

	}

	public static void main(String[] args) {
		SpringApplication.run(ExampleApplication.class, args);
	}

}