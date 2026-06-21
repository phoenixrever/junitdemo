# Mockito + 泛型擦除导致 @InjectMocks 注入错位问题排查文档

## 一、现象回顾

`MyClass` 结构：

```java
@RequiredArgsConstructor
public class MyClass {
    private final GenericService<Foo> fooService;
    private final GenericService<Bar> barService;
    private final GenericService<Baz> bazService;

    public Foo callFoo(Foo input) { return fooService.exec(new Request<>(input)); }
    public Bar callBar(Bar input) { return barService.exec(new Request<>(input)); }
    public Baz callBaz(Baz input) { return bazService.exec(new Request<>(input)); }
}
```

测试中用 `@Mock` + `@InjectMocks`，实测结果：

| 测试目标 | 实际表现 |
|---|---|
| `fooService`（第 1 个字段） | ✅ 注入正确，stub 生效，结果正常 |
| `barService`（第 2 个字段） | ❌ 注入成了 `fooService` 的 mock，结果为 `null`，且报 `UnnecessaryStubbingException` |

同一个类、同一套写法，第一个字段没事，第二个字段直接错位。下面解释这是为什么。

---

## 二、根本机制：为什么第一个对、第二个错

### 2.1 类型擦除造成"候选混在一起"

`GenericService<Foo>`、`GenericService<Bar>`、`GenericService<Baz>` 在 Java 运行时统一变成裸类型 `GenericService`。

`@InjectMocks` 在做**构造方法注入**时，会按构造方法的参数顺序，逐个去"候选 mock 池"里找类型匹配的 mock。候选池就是你测试类里所有 `@Mock` 字段组成的集合：

```
候选池 = [ fooService_mock, barService_mock, bazService_mock ]
```

因为类型擦除，**这三个 mock 对“任意一个”构造参数来说，类型都是匹配的**——Mockito 单看类型，完全分不出谁该配给谁。

### 2.2 名字匹配（NameBasedCandidateFilter）大多数情况下不生效

Mockito 设计上有一层"按名字消歧义"的兜底逻辑：当类型匹配出多个候选时，会尝试拿"构造方法形参名"去对比 mock 的名字（mock 名字默认等于你 `@Mock` 字段的变量名）。

但构造方法形参名能不能被反射读到，**取决于编译时是否开启了 `-parameters` 编译参数**。绝大多数项目默认是**没开**的（除非显式在 `maven-compiler-plugin` 里配置，或者用了某些会自动开启它的脚手架）。没开的话，反射拿到的形参名是 `arg0`、`arg1`、`arg2`，跟 `fooService`、`barService`、`bazService` 这些 mock 名字根本对不上,这层"按名字"过滤直接失效。

### 2.3 失效之后：兜底逻辑"重复选中同一个 mock"

名字匹配失效后，Mockito 退到最后一层兜底——**直接从候选池里取一个能匹配类型的 mock**。

关键问题在这里：**候选池在多个构造参数之间并没有被正确"消费/移除"**。也就是说，对第 1 个参数（`fooService`）和第 2 个参数（`barService`）做类型匹配时，看到的候选池**都是同一份完整列表** `[foo, bar, baz]`，而不是"foo 用过了，剩下 [bar, baz] 给后面的参数挑"。

兜底逻辑每次都固定选**候选池里排在最前面的那一个**（也就是你在测试类里最先声明的 `@Mock` 字段，本例中是 `fooService`）。所以实际发生的是：

```
第1个构造参数(fooService 应该拿什么) → 兜底选了候选池第一个 → fooService_mock   ✅ 碰巧蒙对了（因为它本来就该拿"第一个"）
第2个构造参数(barService 应该拿什么) → 兜底选了候选池第一个 → fooService_mock   ❌ 错的，本该拿第二个，但拿到了第一个
第3个构造参数(bazService 应该拿什么) → 兜底选了候选池第一个 → fooService_mock   ❌（大概率，未实测但符合同一规律）
```

**这就是为什么"第一个字段永远看起来是对的，后面的字段全错"——不是因为第一个字段被特殊照顾，而是因为兜底算法每次都固定吐出"候选池里第一个匹配类型的 mock"，而第一个构造参数刚好也"应该"拿第一个，纯属位置上的巧合，跟后面字段的命运完全不是一回事。**

字段越多，这个问题越明显：只要是同 raw type 的字段，从第 2 个开始基本全部都会被错误地塞成第 1 个字段对应的 mock。

### 2.4 一句话总结根因

> 类型擦除让 Mockito 无法用类型区分多个候选 mock；按参数名消歧义这条路在大多数项目默认编译配置下根本没生效；兜底逻辑没有正确地在多个构造参数之间"消费"候选池，导致所有同类型的字段最终都指向候选池里最先声明的那一个 mock。第一个字段"看起来没问题"只是因为它本来就该拿这个值,纯属位置巧合。

---

## 三、为什么 `ReflectionTestUtils.setField` 能解决问题

```java
ReflectionTestUtils.setField(myClass, "fooService", fooService);
ReflectionTestUtils.setField(myClass, "barService", barService);
ReflectionTestUtils.setField(myClass, "bazService", bazService);
```

这行代码做的事情极其简单、**完全绕开了上面那整套"猜候选"的逻辑**：

- 第一个参数是目标对象
- 第二个参数是**精确的字段名字符串**（你自己写的，不是反射推断出来的）
- 第三个参数是**你手里这个具体的 mock 引用**（不是从候选池里挑出来的，是你直接传进去的）

底层就是：

```java
Field field = target.getClass().getDeclaredField(fieldName);
field.setAccessible(true);
field.set(target, value);
```

**没有类型匹配、没有候选池、没有消歧义，就是按你给的字符串精确反射赋值。** 只要你字符串没写错，结果就一定对，不存在"猜错"的可能性。

这也是为什么之前建议的方案是：**让 `@InjectMocks` 正常处理那些类型唯一、不冲突的字段（这部分它处理得没问题），只对会冲突的同 raw type 字段，用 `setField` 显式纠正一次**——相当于"信任它能处理的部分，亲手修正它处理不好的部分"。

---

## 四、`when().thenAnswer()` vs `doAnswer().when()`，该用哪个

两段代码对比：

```java
// 写法 A
when(barService.exec(any())).thenAnswer(invocation -> {
    Request<Bar> req = invocation.getArgument(0);
    return new Bar("processed-" + req.getPayload().getCode());
});

// 写法 B
doAnswer(invocation -> {
    Request<Foo> request = invocation.getArgument(0);
    Foo foo = request.getPayload();
    return new Foo("processed");
}).when(fooService).exec(any());
```

### 4.1 两者的本质区别

| | `when(mock.method()).thenX()` | `doX().when(mock).method()` |
|---|---|---|
| 调用方式 | 先**真的调用一次** `mock.method()`，再接 `.thenX()` | 先构造 `doX()`，再调用 `.when(mock).method()` |
| 能否用于 `void` 方法 | ❌ 不能（`void` 方法没有返回值传给 `when()`） | ✅ 可以，这是它**唯一不可替代**的场景 |
| 用在 spy（`@Spy`）上时 | ⚠️ 危险：`when()` 里那次"真的调用"会执行**真实方法体**，可能有副作用（比如真的发了请求、真的改了数据库） | ✅ 安全：不会触发真实方法 |
| 可读性 | 更直观，"调用这个方法时返回什么"，符合自然语言顺序 | 稍微绕一点，"做这个动作，当调用这个方法时" |
| 编译期类型检查 | ✅ `thenReturn(xxx)` 的 `xxx` 类型会被编译器检查是否匹配返回类型 | 一般，`Answer` 接口返回 `Object`，类型检查弱一些 |

### 4.2 你这个场景该用哪个

`exec()` 方法**有返回值**（不是 `void`），并且 `fooService`/`barService`/`bazService` 都是用 `@Mock` 创建的**纯 mock**（不是 `@Spy`），不存在"调用时触发真实逻辑"的风险。

**结论：这种情况下用写法 A（`when().thenAnswer()`）更合适，是更标准、更推荐的写法。** 写法 B（`doAnswer().when()`）主要是给下面两种场景准备的：

1. 方法是 `void`，没法塞进 `when()` 里
2. 对象是 `@Spy`（部分真实对象），需要避免 `when()` 触发真实方法的副作用

你现在两种场景都不沾边，纯粹是 mock 一个有返回值的方法，**没有理由用 doAnswer，统一用 when().thenAnswer() / when().thenReturn() 就够了**，可读性也更好。

### 4.3 简单的选择口诀

```
方法有返回值 + 对象是纯 mock  → 用 when().thenReturn() / thenAnswer()
方法是 void                  → 必须用 doNothing()/doThrow()/doAnswer().when()
对象是 @Spy                  → 推荐用 doX().when()，避免触发真实逻辑
```

---

## 五、最终结论与最佳实践清单

1. **同一个 raw type 出现 2 个及以上不同泛型参数的字段时，不要信任 `@InjectMocks` 自动消歧义**——类型擦除 + 参数名通常不可读 + 候选池消费逻辑有缺陷，三个因素叠加导致从第 2 个同类型字段开始大概率被错误地塞成第 1 个字段的 mock。

2. **修复方式**：保留 `@InjectMocks` 处理其余正常字段（它对类型唯一的字段是可靠的），只对冲突的同类型字段额外用 `ReflectionTestUtils.setField(myClass, "字段名", mock)` 显式纠正。这样不需要手写完整的构造方法调用,字段再多也只需要多加几行 `setField`。

3. **判断一个测试是否踩了这个坑的最快方法**：在 `@BeforeEach` 之后加一行
   ```java
   assertThat(ReflectionTestUtils.getField(myClass, "xxxService")).isSameAs(xxxServiceMock);
   ```
   只要这一行能稳定通过,说明注入是对的；如果失败,说明踩中了本文档描述的问题。

4. **Stub 写法**：方法有返回值且对象是纯 mock，统一用 `when().thenReturn()` / `when().thenAnswer()`；只有 `void` 方法或 `@Spy` 对象才用 `doX().when()`。

5. **长期建议**：如果项目里这种"同 raw type 不同泛型字段"的情况大量存在,考虑在设计层面把每种泛型实例化成具体的子类型/包装类型（比如 `FooService implements GenericService<Foo>`），从根上消除运行时类型擦除带来的歧义,这样 `@InjectMocks` 才能完全不出问题,也不需要每次都手动 `setField` 修正。
