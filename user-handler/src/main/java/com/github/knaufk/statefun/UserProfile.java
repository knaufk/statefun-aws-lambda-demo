package com.github.knaufk.statefun;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class UserProfile {
  private final String name;
  private final int seenCount;
  private final long lastSeenMs;

  @JsonCreator
  public UserProfile(
      @JsonProperty("name") String name,
      @JsonProperty("seen_count") int seenCount,
      @JsonProperty("last_seen_ms") long lastSeenMs) {
    this.name = name;
    this.seenCount = seenCount;
    this.lastSeenMs = lastSeenMs;
  }

  public String getName() {
    return name;
  }

  public int getSeenCount() {
    return seenCount;
  }

  public long getLastSeenMs() {
    return lastSeenMs;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("UserProfile{");
    sb.append("name='").append(name).append('\'');
    sb.append(", seenCount='").append(seenCount).append('\'');
    sb.append(", lastSeenMs=").append(lastSeenMs);
    sb.append('}');
    return sb.toString();
  }
}
