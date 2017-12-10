# Retrofit源码解读(二)--Retrofit中网络通信相关

标签（空格分隔）： Retrofit源码

---
## 网络通信八步走
1、创建Retrofit实例
2、定义一个网络请求接口并对接口中的方法添加注解
3、通过 **动态代理** 生成网络请求对象
4、通过**网络请求适配器**,将网络请求对象进行平台适配
5、通过**网络请求执行器**,发送网络请求
6、通过**数据转换器**,解析服务器返回的数据
7、通过**回调执行器**,切换线程
8、用户在**主线程**编写逻辑


### 创建Retrofit实例
```
 public static Retrofit getRetrofit() {

        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(10, TimeUnit.SECONDS);


        return new Retrofit.Builder()
                .client(builder.build())
                .baseUrl("http://m2.qiushibaike.com/")  //设置请求网络地址的url(基地址  这个地址一定要"/"结尾  要不会出现问题)
                .addConverterFactory(GsonConverterFactory.create()) //设置数据解析器  默认使用的Gson
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create()) //设置支持RxJava转换器
                .build();

    }
```


####  Retrofit的成员变量
```
 //map对象,key值是Method 请求方法,value值是ServiceMethod  主要代表我们的网络请求方法进行注解之后的解析使用  
 //serviceMethodCache 用于缓存网络请求配置,方法,数据转换器 适配器
  private final Map<Method, ServiceMethod<?, ?>> serviceMethodCache = new ConcurrentHashMap<>();
//请求网络的okhttp工厂  默认的就是这个
  final okhttp3.Call.Factory callFactory;
  //请求基地址
  final HttpUrl baseUrl;
  //数据转换器工厂集合  用于数据请求得到的response进行转换
  final List<Converter.Factory> converterFactories;
  //网络请求适配器工厂集合  比如RxJava的call
  final List<CallAdapter.Factory> adapterFactories;
  //回调执行器 默认的是MainThreadExecutor
  final @Nullable Executor callbackExecutor;
  //标志位   是否需要立即解析接口中的方法  后面解析的时候会看到
  final boolean validateEagerly;
```

#### Builder内部类
内部类中的成员变量
```
    //平台 ios  android  java8
    private final Platform platform;
    //请求网络的okhttp工厂  默认的就是这个
    private @Nullable okhttp3.Call.Factory callFactory;
      //请求基地址  需要转换的 
    private HttpUrl baseUrl;
     //数据转换器工厂集合  用于数据请求得到的response进行转换
    private final List<Converter.Factory> converterFactories = new ArrayList<>();
     //网络请求适配器工厂集合  比如RxJava的call
    private final List<CallAdapter.Factory> adapterFactories = new ArrayList<>();
     //回调执行器 默认的是MainThreadExecutor
    private @Nullable Executor callbackExecutor;
      //标志位   是否需要立即解析接口中的方法  后面解析的时候会看到
    private boolean validateEagerly;
```
可以看到这个内部类的成员变量和retrofit的成员变量是一样的,因为这个是建造者模式,对于一些的添加的变量和外部类是一样的。构建者模式就是通过这些配置,将一些成员变量进行初始化

##### Builder无参数构造方法
```
 public Builder() {
      this(Platform.get());
    }
    
    可以看出Platform  这个类是个单例 通过findPlatform()方法获取到这个单例
    
    其中的get()方法就是findPlatform()的方法
    在这个方法里面,通过反射来获取类名,在安卓中就是返回new Android()
    
    //也就是下方的这个Android类
    static class Android extends Platform {
    //会有一个默认的回调执行器MainThreadExecutor  Looper.getMainLooper()  也就是主线程  
    @Override public Executor defaultCallbackExecutor() {
      return new MainThreadExecutor();
    }

//创建默认的网络请求适配器工厂  创建默认的网络请求适配器回调
//这个默认请求工厂产生的calladapter让我们的call请求在异步调用的时候会指定我们的Executor执行器 让他执行回调
    @Override CallAdapter.Factory defaultCallAdapterFactory(@Nullable Executor callbackExecutor) {
      if (callbackExecutor == null) throw new AssertionError();
      return new ExecutorCallAdapterFactory(callbackExecutor);
    }

    static class MainThreadExecutor implements Executor {
      private final Handler handler = new Handler(Looper.getMainLooper());

      @Override public void execute(Runnable r) {
        handler.post(r);
      }
    }
  }
  

    
```
##### Builder有参数构造方法
```
 Builder(Platform platform) {
 //做好Platform的初始化
      this.platform = platform;
      // Add the built-in converter factory first. This prevents overriding its behavior but also
      // ensures correct behavior when using converters that consume all types.
      
      //在数据转换工厂集合中添加一个内置的BuiltInConverters对象  也就是说如果当我们没有添加或者没有指定数据转换工厂(一般会指定为GsonConverterFactory为我们的数据转换工厂)
      converterFactories.add(new BuiltInConverters());
    }

```


>由此我们可以看出来,Builder这个内部类主要是构建了我们的使用平台,配置了数据转换器工厂(默认),网络适配器的工厂,以及我们的Ececutor(默认值的初始化),这个地方还没有真正的配置到retrofit的成员变量中。

#### baseUrl(string) 方法
```
 public Builder baseUrl(String baseUrl) {
    //工具类检测我们传入的url是否为空
      checkNotNull(baseUrl, "baseUrl == null");
      //通过parse方法将String类型的Url转换为HttpUrl类型的成员变量
      HttpUrl httpUrl = HttpUrl.parse(baseUrl);
      if (httpUrl == null) {
        throw new IllegalArgumentException("Illegal URL: " + baseUrl);
      }
      //最后通过baseUrl()这个方法来返回Builder这个类型
      return baseUrl(httpUrl);
    }
    
    //
      public Builder baseUrl(HttpUrl baseUrl) {
      //判空处理
      checkNotNull(baseUrl, "baseUrl == null");
      //拆分成多个片段
      List<String> pathSegments = baseUrl.pathSegments();
      //判断集合中的最后一个是否以“/”结尾
      if (!"".equals(pathSegments.get(pathSegments.size() - 1))) {
      //在这里可以看出来传入的url必须以 “/”  结尾
        throw new IllegalArgumentException("baseUrl must end in /: " + baseUrl);
      }
      this.baseUrl = baseUrl;
      return this;
    }

```

#### addConverterFactory()方法
```
  /** Add converter factory for serialization and deserialization of objects. */
    public Builder addConverterFactory(Converter.Factory factory) {
      converterFactories.add(checkNotNull(factory, "factory == null"));
      return this;
    }
    
    我们来看下我们传入的Factory这个对象GsonConverterFactory
    因为我们是通过GsonConverterFactory.create()方法传入的
    
    public static GsonConverterFactory create() {
    //创建Gson对象
    return create(new Gson());
  }
  
    public static GsonConverterFactory create(Gson gson) {
    return new GsonConverterFactory(gson);
  }

 private GsonConverterFactory(Gson gson) {
    if (gson == null) throw new NullPointerException("gson == null");
    //将传入的gson对象赋值给我们GsonConverterFactory里面创建的gson成员变量
    this.gson = gson;
  }
    
```

#### addCallAdapterFactory()方法
```
   /**
     * Add a call adapter factory for supporting service method return types other than {@link
     * Call}.
     */
    public Builder addCallAdapterFactory(CallAdapter.Factory factory) {
      adapterFactories.add(checkNotNull(factory, "factory == null"));
      return this;
    }
    
    我们来看下RxJavaCallAdapterFactory.create()
    
      public static RxJavaCallAdapterFactory createWithScheduler(Scheduler scheduler) {
    if (scheduler == null) throw new NullPointerException("scheduler == null");
    return new RxJavaCallAdapterFactory(scheduler, false);
  }
  
    private RxJavaCallAdapterFactory(Scheduler scheduler, boolean isAsync) {
    //主要的是这个Scheduler   用于RxJava中的调度器
    this.scheduler = scheduler;
    this.isAsync = isAsync;
  }


```

>由此我们可以看出来addConverterFactory()和addCallAdapterFactory()方法很类似,就是创建默认的(我们想要使用的数据转换器和网络适配器),并对  converterFactories 、adapterFactories这两个进行添加数据

#### build()方法
```
public Retrofit build() {
//首先判断baseUrl  这个不能为空  
      if (baseUrl == null) {
        throw new IllegalStateException("Base URL required.");
      }
    //这个网络请求执行器 用于产生call  代表实际的http请求
      okhttp3.Call.Factory callFactory = this.callFactory;
      //如果为空  就会创建一个OkHttpClient  这里也就是说retrofit默认就是Okhttp作为底层网络请求
      if (callFactory == null) {
        callFactory = new OkHttpClient();
      }
    //初始化回调方法的执行器  用在retrofit异步请求的时候(同步请求是不需要的)  子线程耗时操作-->UI线程更新UI
      Executor callbackExecutor = this.callbackExecutor;
      //如果为空的是 就会创建一个默认的回调执行器  这个默认的是在platform里面的默认创建方法  也就是主线程的那个
      if (callbackExecutor == null) {
        callbackExecutor = platform.defaultCallbackExecutor();
      }

      // Make a defensive copy of the adapters and add the default Call adapter.
      //网络请求适配器集合   将成员变量当中的网络请求适配器工厂作为参数传到arraylist的构造函数当中  主要用于配置网络请求适配器的工厂
      List<CallAdapter.Factory> adapterFactories = new ArrayList<>(this.adapterFactories);
      //创建好网络请求适配器工厂之后 添加我们这个安卓平台的默认的网络请求适配器
      adapterFactories.add(platform.defaultCallAdapterFactory(callbackExecutor));

      // Make a defensive copy of the converters.
      //
      List<Converter.Factory> converterFactories = new ArrayList<>(this.converterFactories);
        //这个时候传入到Retrofit的构造方法中  创建Retrofit对象
      return new Retrofit(callFactory, baseUrl, converterFactories, adapterFactories,
          callbackExecutor, validateEagerly);
    }
```

这个时候我们简单的过了创建Retrofit实例的过程,可以通过建造者模式来对相应的数值进行初始化,配置相关的数据转换器和适配器,最后通过build方法完成创建Retrofit的实例。
