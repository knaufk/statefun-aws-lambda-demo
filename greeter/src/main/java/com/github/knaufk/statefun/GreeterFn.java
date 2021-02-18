package com.github.knaufk.statefun;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.apache.flink.statefun.sdk.java.Context;
import org.apache.flink.statefun.sdk.java.StatefulFunction;
import org.apache.flink.statefun.sdk.java.TypeName;
import org.apache.flink.statefun.sdk.java.ValueSpec;
import org.apache.flink.statefun.sdk.java.io.KinesisEgressMessage;
import org.apache.flink.statefun.sdk.java.message.Message;

final class GreeterFn implements StatefulFunction {

  static final TypeName TYPE = TypeName.typeNameFromString("com.knaufk.fns/greeter");

  static final ValueSpec<Integer> SEEN =
      ValueSpec.named("seen").thatExpireAfterWrite(Duration.ofDays(7)).withIntType();

  static final ValueSpec<Long> LAST_SEEN =
      ValueSpec.named("lastSeenMillis").thatExpireAfterWrite(Duration.ofDays(7)).withLongType();

  static final TypeName GREETS_EGRESS = TypeName.typeNameFromString("com.knaufk/greets");

  @Override
  public CompletableFuture<Void> apply(Context context, Message message) {
    return CompletableFuture.runAsync(
        () -> {
          var storage = context.storage();
          var seen = storage.get(SEEN).orElse(0);
          var lastTime = storage.get(LAST_SEEN).orElse(0L);
          var now = System.currentTimeMillis();

          if (!message.is(MyCustomTypes.USER_TYPE)) {
            throw new IllegalStateException("Not a user type?!");
          }
          User user = message.as(MyCustomTypes.USER_TYPE);
          String name = user.getName();

          context.send(
              KinesisEgressMessage.forEgress(GREETS_EGRESS)
                  .withStream("greetings")
                  .withUtf8PartitionKey(name)
                  .withUtf8Value(
                      "Hello "
                          + name
                          + ", for the "
                          + seen
                          + "th time! Last time was "
                          + (now - lastTime)
                          + "ms ago.")
                  .build());

          storage.set(SEEN, seen + 1);
          storage.set(LAST_SEEN, now);
        });
  }
}
