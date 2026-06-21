package com.phoenixhell;

public class Foo {

  private final String name;

  public Foo(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return "Foo{name='" + name + "'}";
  }
}