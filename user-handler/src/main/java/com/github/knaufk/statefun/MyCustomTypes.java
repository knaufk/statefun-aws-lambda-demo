package com.github.knaufk.statefun;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.statefun.sdk.java.TypeName;
import org.apache.flink.statefun.sdk.java.types.SimpleType;
import org.apache.flink.statefun.sdk.java.types.Type;

public final class MyCustomTypes {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public static final Type<User> USER_TYPE =
      SimpleType.simpleImmutableTypeFrom(
          TypeName.typeNameFromString("com.knaufk/User"),
          objectMapper::writeValueAsBytes,
          bytes -> objectMapper.readValue(bytes, User.class));

  public static final Type<UserProfile> USER_PROFILE_TYPE =
      SimpleType.simpleImmutableTypeFrom(
          TypeName.typeNameFromString("com.knaufk/UserProfile"),
          objectMapper::writeValueAsBytes,
          bytes -> objectMapper.readValue(bytes, UserProfile.class));

  public static void main(String[] args) {}
}
