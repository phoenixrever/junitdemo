package com.phoenixhell;

public interface GenericService<T> {
  T exec(Request<T> request);
}
