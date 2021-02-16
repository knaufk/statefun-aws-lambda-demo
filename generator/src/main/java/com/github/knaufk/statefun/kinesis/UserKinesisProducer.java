package com.github.knaufk.statefun.kinesis;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;
import software.amazon.kinesis.common.ConfigsBuilder;
import software.amazon.kinesis.common.KinesisClientUtil;
import software.amazon.kinesis.coordinator.Scheduler;
import software.amazon.kinesis.exceptions.InvalidStateException;
import software.amazon.kinesis.exceptions.ShutdownException;
import software.amazon.kinesis.lifecycle.events.InitializationInput;
import software.amazon.kinesis.lifecycle.events.LeaseLostInput;
import software.amazon.kinesis.lifecycle.events.ProcessRecordsInput;
import software.amazon.kinesis.lifecycle.events.ShardEndedInput;
import software.amazon.kinesis.lifecycle.events.ShutdownRequestedInput;
import software.amazon.kinesis.processor.ShardRecordProcessor;
import software.amazon.kinesis.processor.ShardRecordProcessorFactory;
import software.amazon.kinesis.retrieval.polling.PollingConfig;

/**
 * This class will run a simple app that uses the KCL to read data and uses the AWS SDK to publish
 * data. Before running this program you must first create a Kinesis stream through the AWS console
 * or AWS SDK.
 */
public class UserKinesisProducer {

  private static final Logger log = LoggerFactory.getLogger(UserKinesisProducer.class);

  public static void main(String... args) {
    if (args.length < 1) {
      log.error(
          "At a minimum, the stream name is required as the first argument. The Region may be specified as the second argument.");
      System.exit(1);
    }

    String namesStream = args[0];
    String greetingsStream = args[1];
    String region = null;
    if (args.length > 1) {
      region = args[2];
    }

    new UserKinesisProducer(namesStream, greetingsStream, region).run();
  }

  private final String namesStream;
  private String greetingsStream;
  private final Region region;
  private final KinesisAsyncClient kinesisClient;

  /**
   * Constructor sets streamName and region. It also creates a KinesisClient object to send data to
   * Kinesis. This KinesisClient is used to send dummy data so that the consumer has something to
   * read; it is also used indirectly by the KCL to handle the consumption of the data.
   */
  private UserKinesisProducer(String namesStream, String greetingsStream, String region) {
    this.namesStream = namesStream;
    this.greetingsStream = greetingsStream;
    this.region = Region.of(ObjectUtils.firstNonNull(region, "us-east-2"));
    this.kinesisClient =
        KinesisClientUtil.createKinesisAsyncClient(
            KinesisAsyncClient.builder().region(this.region));
  }

  private void run() {

    /** Sends dummy data to Kinesis. Not relevant to consuming the data with the KCL */
    ScheduledExecutorService producerExecutor = Executors.newSingleThreadScheduledExecutor();
    ScheduledFuture<?> producerFuture =
            producerExecutor.scheduleAtFixedRate(this::publishUser, 10, 1, TimeUnit.SECONDS);

    /**
     * Sets up configuration for the KCL, including DynamoDB and CloudWatch dependencies. The final
     * argument, a ShardRecordProcessorFactory, is where the logic for record processing lives, and
     * is located in a private class below.
     */
    DynamoDbAsyncClient dynamoClient = DynamoDbAsyncClient.builder().region(region).build();
    CloudWatchAsyncClient cloudWatchClient = CloudWatchAsyncClient.builder().region(region).build();
    ConfigsBuilder configsBuilder =
            new ConfigsBuilder(
                    greetingsStream,
                    greetingsStream,
                    kinesisClient,
                    dynamoClient,
                    cloudWatchClient,
                    UUID.randomUUID().toString(),
                    new StringProcessorFactory());

    /**
     * The Scheduler (also called Worker in earlier versions of the KCL) is the entry point to the
     * KCL. This instance is configured with defaults provided by the ConfigsBuilder.
     */
    Scheduler scheduler =
            new Scheduler(
                    configsBuilder.checkpointConfig(),
                    configsBuilder.coordinatorConfig(),
                    configsBuilder.leaseManagementConfig(),
                    configsBuilder.lifecycleConfig(),
                    configsBuilder.metricsConfig(),
                    configsBuilder.processorConfig(),
                    configsBuilder
                            .retrievalConfig()
                            .retrievalSpecificConfig(new PollingConfig(greetingsStream, kinesisClient)));

    /**
     * Kickoff the Scheduler. Record processing of the stream of dummy data will continue
     * indefinitely until an exit is triggered.
     */
    Thread schedulerThread = new Thread(scheduler);
    schedulerThread.setDaemon(true);
    schedulerThread.start();
  }

  /** Sends a single record of dummy data to Kinesis. */
  private void publishUser() {

    User nextUser = UserGenerator.getRandomUser();

    PutRecordRequest request = null;
    try {
      request =
          PutRecordRequest.builder()
              .partitionKey(nextUser.getName())
              .streamName(namesStream)
              .data(SdkBytes.fromByteArray(nextUser.toJsonAsBytes()))
              .build();
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    try {
      kinesisClient.putRecord(request).get();
    } catch (InterruptedException e) {
      log.info("Interrupted, assuming shutdown.");
    } catch (ExecutionException e) {
      log.error("Exception while sending data to Kinesis. Will try again next cycle.", e);
    }
  }

  private static class StringProcessorFactory implements ShardRecordProcessorFactory {
    public ShardRecordProcessor shardRecordProcessor() {
      return new StringProcessor();
    }
  }

  private static class StringProcessor implements ShardRecordProcessor {

    private static final String SHARD_ID_MDC_KEY = "ShardId";

    private static final Logger log = LoggerFactory.getLogger(StringProcessor.class);

    private String shardId;

    /**
     * Invoked by the KCL before data records are delivered to the ShardRecordProcessor instance
     * (via processRecords). In this example we do nothing except some logging.
     *
     * @param initializationInput Provides information related to initialization.
     */
    public void initialize(InitializationInput initializationInput) {
      shardId = initializationInput.shardId();
      MDC.put(SHARD_ID_MDC_KEY, shardId);
      try {
        log.info("Initializing @ Sequence: {}", initializationInput.extendedSequenceNumber());
      } finally {
        MDC.remove(SHARD_ID_MDC_KEY);
      }
    }

    /**
     * Handles record processing logic. The Amazon Kinesis Client Library will invoke this method to
     * deliver data records to the application. In this example we simply log our records.
     *
     * @param processRecordsInput Provides the records to be processed as well as information and
     *     capabilities related to them (e.g. checkpointing).
     */
    public void processRecords(ProcessRecordsInput processRecordsInput) {
      MDC.put(SHARD_ID_MDC_KEY, shardId);
      try {
        log.info("Processing {} record(s)", processRecordsInput.records().size());
        processRecordsInput
            .records()
            .forEach(
                r -> {
                  byte[] arr = new byte[r.data().remaining()];
                  r.data().get(arr);
                  System.out.println(new String(arr));
                });
      } catch (Throwable t) {
        log.error("Caught throwable while processing records. Aborting.");
        Runtime.getRuntime().halt(1);
      } finally {
        MDC.remove(SHARD_ID_MDC_KEY);
      }
    }

    /**
     * Called when the lease tied to this record processor has been lost. Once the lease has been
     * lost, the record processor can no longer checkpoint.
     *
     * @param leaseLostInput Provides access to functions and data related to the loss of the lease.
     */
    public void leaseLost(LeaseLostInput leaseLostInput) {
      MDC.put(SHARD_ID_MDC_KEY, shardId);
      try {
        log.info("Lost lease, so terminating.");
      } finally {
        MDC.remove(SHARD_ID_MDC_KEY);
      }
    }

    /**
     * Called when all data on this shard has been processed. Checkpointing must occur in the method
     * for record processing to be considered complete; an exception will be thrown otherwise.
     *
     * @param shardEndedInput Provides access to a checkpointer method for completing processing of
     *     the shard.
     */
    public void shardEnded(ShardEndedInput shardEndedInput) {
      MDC.put(SHARD_ID_MDC_KEY, shardId);
      try {
        log.info("Reached shard end checkpointing.");
        shardEndedInput.checkpointer().checkpoint();
      } catch (ShutdownException | InvalidStateException e) {
        log.error("Exception while checkpointing at shard end. Giving up.", e);
      } finally {
        MDC.remove(SHARD_ID_MDC_KEY);
      }
    }

    /**
     * Invoked when Scheduler has been requested to shut down (i.e. we decide to stop running the
     * app by pressing Enter). Checkpoints and logs the data a final time.
     *
     * @param shutdownRequestedInput Provides access to a checkpointer, allowing a record processor
     *     to checkpoint before the shutdown is completed.
     */
    public void shutdownRequested(ShutdownRequestedInput shutdownRequestedInput) {
      MDC.put(SHARD_ID_MDC_KEY, shardId);
      try {
        log.info("Scheduler is shutting down, checkpointing.");
        shutdownRequestedInput.checkpointer().checkpoint();
      } catch (ShutdownException | InvalidStateException e) {
        log.error("Exception while checkpointing at requested shutdown. Giving up.", e);
      } finally {
        MDC.remove(SHARD_ID_MDC_KEY);
      }
    }
  }
}
