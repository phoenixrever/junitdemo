package com.phoenixhell;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyClassTest {

  @Mock
  private GenericService<Foo> fooService;

  @Mock
  private GenericService<Bar> barService;

  @InjectMocks
  private MyClass myClass;

  @BeforeEach
  void setUp() {
    // myClass = new MyClass(fooService, barService);
  }

  @Test
  void testDoProcessFoo() throws Exception {
    Foo input = new Foo("input");
    Foo expected = new Foo("processed");

    // when(fooService.exec(any())).thenReturn(expected);
    when(fooService.exec(any())).thenAnswer(invocation -> {
      Request<Foo> request = invocation.getArgument(0);
      Foo foo = request.getPayload();
      System.out.println("fooService.exec called");
      return new Foo("processed");
    });

    doAnswer(invocation -> {
      System.out.println("fooService.exec called");
      Request<Foo> request = invocation.getArgument(0);
      Foo foo = request.getPayload();
      return new Foo("processed");
    }).when(fooService).exec(any());

    Method method = MyClass.class.getDeclaredMethod("doProcessFoo", Foo.class);
    method.setAccessible(true);
    String result = (String) method.invoke(myClass, input);

    assertThat(result).isEqualTo("processed");
  }
}
