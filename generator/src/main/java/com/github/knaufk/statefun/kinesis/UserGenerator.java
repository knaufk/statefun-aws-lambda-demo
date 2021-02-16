/*
 * Copyright 2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Amazon Software License (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 * http://aws.amazon.com/asl/
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.github.knaufk.statefun.kinesis;


import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class UserGenerator {

  private static final List<User> USERS =
      Arrays.asList(new User("Igal"), new User("Gordon"), new User("Konstantin"));

  private static final Random random = new Random();

  public static User getRandomUser() {
    return USERS.get(random.nextInt(USERS.size()));
  }
}
