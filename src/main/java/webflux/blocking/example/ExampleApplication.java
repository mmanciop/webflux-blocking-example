package webflux.blocking.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.config.EnableWebFlux;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.ipc.netty.http.HttpResources;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static reactor.core.publisher.Mono.fromRunnable;
import static reactor.core.scheduler.Schedulers.fromExecutor;

@EnableWebFlux
@SpringBootApplication
public class ExampleApplication {

	@RestController
	public static class Resource {

		private static final String EXECUTOR_WORKER_THREAD_NAME_PREFIX = "executor-worker-thread-";

		private static final Logger LOGGER = LoggerFactory.getLogger(Resource.class);

		private static final ThreadFactory THREAD_FACTORY = new ThreadFactory() {

			final AtomicInteger counter = new AtomicInteger();

			@Override
			public Thread newThread(final Runnable r) {
				final Thread t = new Thread(r);
				t.setDaemon(true);
				t.setName(EXECUTOR_WORKER_THREAD_NAME_PREFIX + counter.incrementAndGet());
				return t;
			}
		};

		/*
		 * Restrict the amount of threads to be used: using an elastic scheduler or
		 * {@code java.util.concurrent.Executors.newCachedThreadPool()} without any throttling or rate limiting will
		 * potentially cause uncontrolled proliferation of threads, and then one of the main benefits in terms of
		 * memory usage and of using reactive/nio, namely "few threads, always busy", is gone.
		 *
		 * Usually, the maximum amount of threads to be used in parallel to perform blocking work and the maximum idle
		 * time (amount of time to wait before an idle thread is disposed of), would be made configurable and configured
		 * based on the amount of resources available in production and the expected workload.
		 */
		private final static Scheduler BLOCKING_WORK_SCHEDULER = fromExecutor(Executors.newFixedThreadPool(10,
				THREAD_FACTORY));

		/*
		 * This custom scheduler wraps around the way Netty schedules the processing of Http-related tasks.
		 * It can be used to "go back to the scheduling world" of Reactor/Netty.
		 */
		private final static Scheduler REACTOR_HTTP_NIO_SCHEDULER =
				fromExecutor(HttpResources.get().onServer(true));

		@GetMapping("/hello")
		public Mono<String> greetBackAfterDelay(
				@RequestParam(name = "delayInSeconds", defaultValue = "0L")
				final long delayInSeconds) {

			return fromRunnable(() -> {
					try {
						LOGGER.info("Time to go to sleep!");

						Thread.sleep(SECONDS.toMillis(delayInSeconds));

						LOGGER.info("Time to wake up!");
					} catch (InterruptedException ex) {
						throw Exceptions.propagate(ex);
					}
				})
				.doOnNext(ignored -> {
					Assert.isTrue(!Schedulers.isInNonBlockingThread(),
							"Logic should not be on a NonBlocking thread");

					final String currentThreadName = Thread.currentThread().getName();

					Assert.isTrue(currentThreadName.startsWith("executor-worker-thread-"),
							"Logic should execute on the executor thread");
				})
				.subscribeOn(BLOCKING_WORK_SCHEDULER)
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