# Retrofit源码解读(三)--RxJavaCallAdapterFactory讲解

标签（空格分隔）：Retrofit源码

---

#### 前言

* 以下的相关知识总结是通过慕课网的相关学习和自己的相关看法,如果有需要的可以去查看一下慕课网的相关教学,感觉还可以。

### 父类
which uses RxJava for creating observables
```
 final class RxJavaCallAdapterFactory extends CallAdapter.Factory
```
我们可以看到这个RxJavaCallAdapterFactory继承的是Factory这个类,我们可以点进入可以看到Factory是CallAdapter这个接口的一个抽象类

#### Calladapter流程

就是把Retrofit里面的Call这个泛型对象转换成我们的java对象,通过这个java对象来进行数据转换,UI显示等等。。
```
Call<T> ---> Java对象
```

>这里的Retrofit里面的Call<T>不同于okhttp里面的Call<T>,这里的是对于OkHttp的一个封装,也就是说我们通过Retrofit来进行网络请求,其实质就是通过OkHttp来进行的网络请求,只不过Retrofit封装了一层。

#### 如何转换成java对象
```
创建Call<T>对象,通过调用OkHttp请求,拿到服务器返回给我们的数据,这个时候通过我们的converter这个数据转换器,把服务器给我们返回回来的response转换为我们需要的java对象,其中这个converter可以在我们创建retrofit实例的时候进行个性化的配置,比如addConverterFactory(GsonConverterFactory.create()) //设置数据解析器  默认使用的Gson
```

#### 源码分析
<br>
##### CallAdapter的成员变量

* Type responseType();这个方法就是返回http请求返回后的类型,这个就是我们接口中的泛型的实参,比如如下:
>Returns the value type that this adapter uses when converting the HTTP response body to a Java
   * object. For example, the response type for {@code Call<Repo>} is {@code Repo}. This type
   * is used to prepare the {@code call} passed to {@code #adapt}.
```
 @GET("article/list/latest?page=1")
    Call<HttpResult<List<TestBean>>> getQiuShiJsonString();
```
    那么这个Type就是HttpResult<List<TestBean>>这个实例


* T adapt(Call<R> call);
    这个就是返回的是Retrofit里面的Call对象,如果是RxJava的话,那么返回的就是一个 Observable<?> 也就是类似下面我们使用RxJava的接口类型
```
 Observable<BaseModel<ArrayList<PersonInfoBean.PersonalFood>>>
```

#### Factory内部抽象类
```
   这个就是根据我们的接口返回类型,注解类型得到我们实际需要的CallAdapter
   /**
     * Returns a call adapter for interface methods that return {@code returnType}, or null if it
     * cannot be handled by this factory.
     */
    public abstract @Nullable CallAdapter<?, ?> get(Type returnType, Annotation[] annotations,
        Retrofit retrofit);
```

```
  /**
     * Extract the upper bound of the generic parameter at {@code index} from {@code type}. For
     * example, index 1 of {@code Map<String, ? extends Runnable>} returns {@code Runnable}.
     */
    protected static Type getParameterUpperBound(int index, ParameterizedType type) {
      return Utils.getParameterUpperBound(index, type);
    }
```

```
获取我们的原始的类型
 /**
     * Extract the raw class type from {@code type}. For example, the type representing
     * {@code List<? extends Runnable>} returns {@code List.class}.
     */
    protected static Class<?> getRawType(Type type) {
      return Utils.getRawType(type);
    }
```

#### RxJavaCallAdapterFactory代码
1、继承Factory
```
public final class RxJavaCallAdapterFactory extends CallAdapter.Factory {
```
2、注册CallAdapter
```
addCallAdapterFactory(RxJavaCallAdapterFactory.create())
```
3、调用Factory.get方法来获取我们的CallAdapter


```
 @Override
  public CallAdapter<?, ?> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {
    //获取到原始数据类型
    Class<?> rawType = getRawType(returnType);
    以下代码省略
    / 。。。/
   
    //最终返回一个RxJavaCallAdapter  进行类型转换
    return new RxJavaCallAdapter(responseType, scheduler, isAsync, isResult, isBody, isSingle,
        false);
  }
 
```

4、调用adapt方法来讲Call请求转换
```
  @Override public Object adapt(Call<R> call) {
  
  //这个时候把retrofit的Call这个对象传进来,这个然后通过
    OnSubscribe<Response<R>> callFunc = isAsync
        ? new CallEnqueueOnSubscribe<>(call)
        : new CallExecuteOnSubscribe<>(call);

    OnSubscribe<?> func;
    if (isResult) {
      func = new ResultOnSubscribe<>(callFunc);
    } else if (isBody) {
      func = new BodyOnSubscribe<>(callFunc);
    } else {
      func = callFunc;
    }
    // 通过Observable<?> observable 和Call创建关联 可以看到func的创建和Call是有关联的
    Observable<?> observable = Observable.create(func);
    //判断调度器是否为空
    if (scheduler != null) {
      observable = observable.subscribeOn(scheduler);
    }
    
    if (isSingle) {
      return observable.toSingle();
    }
    if (isCompletable) {
      return CompletableHelper.toCompletable(observable);
    }
    //直接返回observable
    return observable;
  }
```

### 总结

* 理论：
获取到一个Call<T>对象,拿到这个Call<T>对象去执行http请求,而Retrofit调用这个Call请求,最终调用的还是okhttp里面的Call请求,只不过对其进行了封装,通过这样我们就可以获取到服务器端返回的数据,获取到数据之后我们就会调用converter数据转换器来把我们需要的对象转换出来.

* 实现：
    * 实现Factory这个抽象类
    * 注册CallAdapter到retrofit中
    * 通过Factory.get方法获取到具体的CallAdapter
    * 在调用adapt这个方法,最终转换成每一个平台适用的类型
