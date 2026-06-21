package com.phoenixhell;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 这个测试类主要是为了演示 @InjectMocks 错的的注入行为
 * 
 */
@ExtendWith(MockitoExtension.class)
class MyClassTest2 {

  @Mock
  private GenericService<Foo> fooService;

  @Mock
  private GenericService<Bar> barService;

  @Mock
  private GenericService<Baz> bazService;

  @InjectMocks
  private MyClass myClass;

  @BeforeEach
  void setUp() {
    // myClass = new MyClass(fooService, barService, bazService); // 显式按构造参数顺序传

    // 同じ raw type (GenericService) が複数あるため、
    // @InjectMocks の自動注入では型が区別できず誤った mock が入ることがある。
    // そのため、該当フィールドのみ明示的に正しい mock を再設定している。

    // ジェネリクスの型消去により @InjectMocks が誤注入するため、手動で修正
    ReflectionTestUtils.setField(myClass, "fooService", fooService);
    ReflectionTestUtils.setField(myClass, "barService", barService);
    ReflectionTestUtils.setField(myClass, "bazService", bazService);
  }

  @Test
  void showWhichMockReallyGotInjected() {
    Object injectedFoo = ReflectionTestUtils.getField(myClass, "fooService");
    Object injectedBar = ReflectionTestUtils.getField(myClass, "barService");
    Object injectedBaz = ReflectionTestUtils.getField(myClass, "bazService");

    // 打印每个字段实际拿到的是不是“对应”的那个 mock
    System.out.println("myClass.fooService == @Mock fooService ? " + (injectedFoo == fooService));
    System.out.println("myClass.barService == @Mock barService ? " + (injectedBar == barService));
    System.out.println("myClass.bazService == @Mock bazService ? " + (injectedBaz == bazService));

    // 再打印实际拿到的是谁
    System.out.println("myClass.fooService 实际是: " + injectedFoo);
    System.out.println("myClass.barService 实际是: " + injectedBar);
    System.out.println("myClass.bazService 实际是: " + injectedBaz);
  }

  @Test
  void showBarStubFires() {
    when(barService.exec(any())).thenAnswer(invocation -> {
      System.out.println(">>> barService.exec 真的被调用了 <<<");
      Request<Bar> req = invocation.getArgument(0);
      return new Bar("processed-" + req.getPayload().getCode());
    });

    Bar result = myClass.callBar(new Bar("BAR001"));
    System.out.println("最终拿到的结果: " + result);
  }
}