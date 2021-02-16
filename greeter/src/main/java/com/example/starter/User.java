package com.example.starter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class User {
    private final String name;

    @JsonCreator
    public User(@JsonProperty("name") String name) {
        this.name = Objects.requireNonNull(name);
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "User{" +
                "name='" + name + '\'' +
                '}';
    }
}
