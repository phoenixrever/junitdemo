package com.phoenixhell;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MyClass {

  private final GenericService<Foo> fooService;
  private final GenericService<Bar> barService;
  private final GenericService<Baz> bazService;

  public Foo callFoo(Foo input) {
    return fooService.exec(new Request<>(input));
  }

  public Bar callBar(Bar input) {
    return barService.exec(new Request<>(input));
  }

  public Baz callBaz(Baz input) {
    return bazService.exec(new Request<>(input));
  }
}