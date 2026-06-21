package com.phoenixhell;

public class Request<T> {
  private final T payload;

  public Request(T payload) {
    this.payload = payload;
  }

  public T getPayload() {
    return payload;
  }
}
