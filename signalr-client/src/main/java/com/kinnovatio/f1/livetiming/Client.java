package com.kinnovatio.f1.livetiming;

import com.kinnovatio.signalr.F1HubConnection;
import com.kinnovatio.signalr.messages.LiveTimingMessage;
import com.microsoft.signalr.HubConnection;
import com.microsoft.signalr.HubConnectionBuilder;
import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.Unit;
import io.reactivex.rxjava3.disposables.Disposable;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Optional;

public class Client {
    private static final Logger LOG = LoggerFactory.getLogger(Client.class);

    /*
    Configuration section. The configuration values are read from the following locations (in order of precedence):
    1. Environment variables
    2. Configuration file at /config/config.yaml
    3. The default config file at ./resources/META-INF/microprofile-config.yaml (packaged with the code)

    A configuration variable "foo.bar" resolves to the following input:
    - Environment variable named "foo_bar" (dot "." is replaced by underscore "_")
    - Config file yaml entry: "
    metrics:
        bar: "the-value"
    "
     */
    // Connection variables
    private static final String signalRBaseUrl = "https://livetiming.formula1.com/signalr/";
    private static final String testBaseUrl = "http://livetiming.kinnovatio.local/signalr/";


    // Metrics configs. From config file / env variables
    private static final boolean enableMetrics =
            ConfigProvider.getConfig().getValue("metrics.enable", Boolean.class);
    private static final String metricsJobName = ConfigProvider.getConfig().getValue("metrics.jobName", String.class);
    private static final Optional<String> pushGatewayUrl =
            ConfigProvider.getConfig().getOptionalValue("metrics.pushGateway.url", String.class);

    /*
    Metrics section. Define the metrics to expose.
     */
    //JvmMetrics.builder().register(); // initialize the out-of-the-box JVM metrics
    static final PrometheusRegistry collectorRegistry = new PrometheusRegistry();
    static final Gauge newJobDurationSeconds = Gauge.builder()
            .name("job.duration_seconds").help("Job duration in seconds")
            .unit(Unit.SECONDS)
            .register(collectorRegistry);

    static final Gauge newErrorGauge= Gauge.builder()
            .name("job.errors").help("Total job errors")
            .register(collectorRegistry);

    /*
    The entry point of the code. It executes the main logic and push job metrics upon completion.
     */
    public static void main(String[] args) {
        boolean jobFailed = false;
        try {
            // Execute the main logic
            run();

        } catch (Exception e) {
            LOG.error("Unrecoverable error. Will exit. {}", e.toString());
            errorGauge.inc();
            jobFailed = true;
        } finally {
            if (enableMetrics) {
                pushMetrics();
            }
            if (jobFailed) {
                System.exit(1); // container exit code for execution errors, etc.
            }
        }
    }

    /*
    The main logic to execute.
     */
    private static void run() throws Exception {
        LOG.info("Starting container...");
        Gauge.Timer jobDurationTimer = jobDurationSeconds.startTimer();

        useSignalrCustomClient();
        //useSignalrCoreClient();
        LOG.info("Finished work");
        jobDurationTimer.setDuration();

        // The job completion metric is only added to the registry after job success,
        // so that a previous success in the Pushgateway isn't overwritten on failure.
        Gauge jobCompletionTimeStamp = Gauge.build()
                .name("job_completion_timestamp").help("Job completion time stamp").register(collectorRegistry);
        jobCompletionTimeStamp.setToCurrentTime();
    }

    private static void useSignalrCustomClient() throws Exception {
        F1HubConnection hub = F1HubConnection.of(testBaseUrl)
                //.enableMessageLogging(true)
                .withConsumer(Client::processMessage)
                ;
        if (hub.connect()) {
            LOG.info("Received connection confirmation.");
            LOG.info("Start subscription.");
            hub.subscribeToAll();
        }

        //Thread.sleep(25000);

        //hub.close();
    }

    private static void useSignalrCoreClient() throws InterruptedException {
        LOG.info("Connect to hub...");
        HubConnection hubConnection = HubConnectionBuilder.create("https://livetiming.formula1.com/signalr")
                .build();
        hubConnection.start().blockingAwait();
        LOG.info("Hub connection state: {}", hubConnection.getConnectionState().toString());

        Disposable messageStreamDisposable = hubConnection.stream(String.class, "Subscribe", "Heartbeat")
                .onErrorReturnItem("Error in stream")
                .subscribe(message -> System.out.println("Message received:/n" + message + "/n"));

        Thread.sleep(3000);
        messageStreamDisposable.dispose();

        hubConnection.close();
    }

    private static void processMessage(LiveTimingMessage message) {
        LOG.info("Received live timing message: {}", message);
    }

    /*
    Push the current metrics to the push gateway.
     */
    private static boolean pushMetrics() {
        boolean isSuccess = false;
        if (pushGatewayUrl.isPresent()) {
            try {
                LOG.info("Pushing metrics to {}", pushGatewayUrl);
                PushGateway pg = new PushGateway(new URL(pushGatewayUrl.get())); //9091
                pg.pushAdd(collectorRegistry, metricsJobName);
                isSuccess = true;
            } catch (Exception e) {
                LOG.warn("Error when trying to push metrics: {}", e.toString());
            }
        } else {
            LOG.warn("No metrics push gateway configured. Cannot push the metrics.");
        }

        return isSuccess;
    }
}
