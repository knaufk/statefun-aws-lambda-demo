package com.github.knaufk.statefun;

import static com.github.knaufk.statefun.MyCustomTypes.USER_PROFILE_TYPE;
import static java.util.concurrent.CompletableFuture.runAsync;

import java.util.concurrent.CompletableFuture;
import org.apache.flink.statefun.sdk.java.*;
import org.apache.flink.statefun.sdk.java.message.Message;
import org.apache.flink.statefun.sdk.java.message.MessageBuilder;

final class UserFn implements StatefulFunction {

  static final TypeName TYPE = TypeName.typeNameFromString("com.knaufk.fns/user");

  public static final ValueSpec<Integer> SEEN_COUNT = ValueSpec.named("seen_count").withIntType();

  public static final ValueSpec<Long> LAST_SEEN_MS =
      ValueSpec.named("last_seen_ms").withLongType();

  @Override
  public CompletableFuture<Void> apply(Context context, Message message) {
    return runAsync(
        () -> {
          AddressScopedStorage storage = context.storage();

          int seenCount = storage.get(SEEN_COUNT).orElse(0);
          seenCount++;

          final long nowMs = System.currentTimeMillis();
          final long lastSeenTimestampMs = storage.get(LAST_SEEN_MS).orElse(nowMs);

          storage.set(SEEN_COUNT, seenCount);
          storage.set(LAST_SEEN_MS, nowMs);

          User user = message.as(MyCustomTypes.USER_TYPE);
          String name = user.getName();

          UserProfile profile = new UserProfile(name, seenCount, nowMs-lastSeenTimestampMs);

          context.send(
              MessageBuilder.forAddress(
                      TypeName.typeNameFromString("com.knaufk.fns/greeter"), profile.getName())
                  .withCustomType(USER_PROFILE_TYPE, profile)
                  .build());
        });
  }
}
