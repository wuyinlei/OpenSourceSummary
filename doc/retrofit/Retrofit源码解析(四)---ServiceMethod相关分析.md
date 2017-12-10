# Retrofit源码解析(四)---ServiceMethod相关分析

标签（空格分隔）： Retrofit源码

---


#### 前言

* 以下的相关知识总结是通过慕课网的相关学习和自己的相关看法,如果有需要的可以去查看一下慕课网的相关教学,感觉还可以。

### 定义一个接口
```
    //看下我们创建的这个接口  这个方法返回一个Call<T>对象
    // @GET表示方法是get请求    article/list/latest?page=1表示相对url请求地址  第一部分在baseUrl里面
    //每个方法参数都需要进行注解标志 
    @GET("article/list/latest?page=1")
    Call<HttpResult<List<TestBean>>> getQiuShiJsonString();
```

### 解析create()方法
```
  //这个通过创建的Retrofit的实例创建一个接口服务
     TestInterface service =getRetrofit().create(TestInterface.class);
```
* create方法的定义
```
Create an implementation of the API endpoints defined by the {@code service} interface.
```

```
 public <T> T create(final Class<T> service) {
    //这个方法逻辑是用来判断这个service是否合法
    Utils.validateServiceInterface(service);
    //下面的这个validateEagerly  就是之前的那个标志  判断是否是提前解析这个接口
    if (validateEagerly) {
    //解析在后面
      eagerlyValidateMethods(service);
    }
    //做完上面的判断验证之后  接下来就是关键地方
    //返回了一个网络接口的动态代理对象   实质就是通过动态代理创建网络接口实例的
    return (T)
    //动态代理模式   
    Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[] { service },
        new InvocationHandler() {
          private final Platform platform = Platform.get();

          @Override 
          //
          public Object invoke(Object proxy, Method method, @Nullable Object[] args)
              throws Throwable {
            // If the method is a method from Object then defer to normal invocation.
            //如果调用的是Object方法  
            if (method.getDeclaringClass() == Object.class) {
              return method.invoke(this, args);
            }
            if (platform.isDefaultMethod(method)) {
              return platform.invokeDefaultMethod(method, service, proxy, args);
            }
            //最核心的三行代码  这个在后面进行讲解吧
            ServiceMethod<Object, Object> serviceMethod =
                (ServiceMethod<Object, Object>) loadServiceMethod(method);
            OkHttpCall<Object> okHttpCall = new OkHttpCall<>(serviceMethod, args);
            return serviceMethod.callAdapter.adapt(okHttpCall);
          }
        });
  }
```
* 如果validateEagerly这个标志为true的时候 会提前进入这个方法来验证这个service和解析他。
```
 private void eagerlyValidateMethods(Class<?> service) {
    //判断使用平台   android  ios   java8三个平台
    Platform platform = Platform.get();
    //传递过去的接口方法进行循环  
    for (Method method : service.getDeclaredMethods()) {
      //可以看出isDefaultMethod()方法是返回false  所以一定会走下面的loadServiceMethod()方法
      if (!platform.isDefaultMethod(method)) {
        loadServiceMethod(method);
      }
    }
  }
```
* loadServiceMethod(method)方法查看
```
 ServiceMethod<?, ?> loadServiceMethod(Method method) {
    //private final Map<Method, ServiceMethod<?, ?>> serviceMethodCache = new ConcurrentHashMap<>();   之前版本是LinkedHashMap()  现在的版本是ConcurrentHashMap
    //http://angelbill3.iteye.com/blog/2277062  
    //这个是通过serviceMethodCache的get方法获取到缓存的method
    ServiceMethod<?, ?> result = serviceMethodCache.get(method);
    //如果有这个缓存  那么就直接返回结果
    if (result != null) return result;
    //如果缓存中没有这个数据  锁机制
    synchronized (serviceMethodCache) {
      //再次判断是否有缓存
      result = serviceMethodCache.get(method);
      if (result == null) {  
        //根据ServiceMethod的构建者模式创建一个ServiceMethod对象
        result = new ServiceMethod.Builder<>(this, method).build();
        //把method作为key  value就是result(ServiceMethod)
        serviceMethodCache.put(method, result);
      }
    }
    //返回ServiceMethod这个结果
    return result;
  }
```
#### 核心的三行代码
```
 //就是用来获取到ServiceMethod,比如我们在接口中定义的方法  对应一个接口中定义好的请求方法  比如getCall()方法
 ServiceMethod<Object, Object> serviceMethod =
                (ServiceMethod<Object, Object>) loadServiceMethod(method);
            OkHttpCall<Object> okHttpCall = new OkHttpCall<>(serviceMethod, args);
            return serviceMethod.callAdapter.adapt(okHttpCall);
```

##### ServiceMethod类
相关的成员变量
```
 // Upper and lower characters, digits, underscores, and hyphens, starting with a character.
  static final String PARAM = "[a-zA-Z][a-zA-Z0-9_-]*";
  static final Pattern PARAM_URL_REGEX = Pattern.compile("\\{(" + PARAM + ")\\}");
  static final Pattern PARAM_NAME_REGEX = Pattern.compile(PARAM);
 //网络请求工厂  也就是okhttp的网络请求
  final okhttp3.Call.Factory callFactory;
  //网络请求适配器  把call请求适配各个平台
  final CallAdapter<R, T> callAdapter;
  //网络基地址
  private final HttpUrl baseUrl;
  //数据转换器
  private final Converter<ResponseBody, R> responseConverter;
  //网络请求的http方法  比如get  post方法
  private final String httpMethod;
  //网络请求的相对地址  和baseUrl拼接起来就是实际地址
  private final String relativeUrl;
  //请求头
  private final Headers headers;
  //网络请求的http报文的type
  private final MediaType contentType;
  //三个标志位
  private final boolean hasBody;
  private final boolean isFormEncoded;
  private final boolean isMultipart;
  //方法参数的处理器   比如我们定义的方法  上面的@get和后面的参数
  private final ParameterHandler<?>[] parameterHandlers;
```
可以了解到ServiceMethod完全包含了我们访问网络的所有的基本信息。

首先看下他的构造函数
```
//可以看出是传递的是一个Builder内部类  所以ServiceMethod也是通过构建者模式来初始化他的成员变量的
ServiceMethod(Builder<R, T> builder) {
    
}
//可以看出就是通过build来创建的  最后调用build进行创建
 ServiceMethod result = new ServiceMethod.Builder<>(this, method).build();
 
  //构造方法
   Builder(Retrofit retrofit, Method method) {
      this.retrofit = retrofit;
      this.method = method; //网络请求方法
      //获取网络请求方法中的注解 @GET  @POST
      this.methodAnnotations = method.getAnnotations();
      //获取网络请求方法中的参数类型
      this.parameterTypes = method.getGenericParameterTypes();
      //获取网络请求中的注解内容
      this.parameterAnnotationsArray = method.getParameterAnnotations();
    }
 所以通过这个builder方法就能配置好我们ServiceMethod方法的成员变量
 
 我们来看下build方法做了哪些内容吧,因为最后是调用build方法来完成ServiceMethod的创建
 
    //第一行  创建callAdapter
   callAdapter = createCallAdapter();
   
   //第二行  根据网络请求接口的返回值类型和注解类型从retrofit对象当中获取这个网络适配器返回的数据类型
    responseType = callAdapter.responseType();
    
    //第三行(异常  检查的代码去掉了)
     responseConverter = createResponseConverter();
     
    //第四行   这个就是对接口注解类型进行解析
     for (Annotation annotation : methodAnnotations) {
        parseMethodAnnotation(annotation);
      }
      //这个就是判断  接口需要@GET  @POST  @PUT方式的注解
    if (httpMethod == null) {
      throw methodError("HTTP method annotation is required (e.g., @GET, @POST, etc.).");
    }
    
    //获取到参数注解的长度
     int parameterCount = parameterAnnotationsArray.length;
     //参数解析器初始化
      parameterHandlers = new ParameterHandler<?>[parameterCount];
      //开始循环解析
      for (int p = 0; p < parameterCount; p++) {
        Type parameterType = parameterTypes[p];
        //然后通过parseParameter()这个方法来解析参数  通过我们传入的参数类型和参数的注解内容
        parameterHandlers[p] = parseParameter(p, parameterType, parameterAnnotations);
      }


所以总结的来说就是build方法就是根据我们定义好的请求接口中的返回值类型和方法中的注解来从我们的网络请求适配器工厂和数据转换器工厂集合中分别获取到我们所需要的网络请求适配器和response数据转换器,然后根据参数注解去获取到我们所需要的解析的参数,最后调用parseParameter()方法来解析我们接口中的参数。
 
```
* createCallAdapter()方法
```
//是根据retrofit网络请求中接口的方法的返回值类型和注解的类型从我们的retrofit对象当中获取对应的网络请求适配器
 private CallAdapter<T, R> createCallAdapter() {
 //获取网络请求接口中方法中的返回值类型
      Type returnType = method.getGenericReturnType();
      。。。。
      //获取注解类型
      Annotation[] annotations = method.getAnnotations();
      //最终调用retrofit的callAdapter  返回我们的网络适配器
        return (CallAdapter<T, R>) retrofit.callAdapter(returnType, annotations);
      }
    }
    
    
      public CallAdapter<?, ?> callAdapter(Type returnType, Annotation[] annotations) {
    return nextCallAdapter(null, returnType, annotations);
  }
  
  
   public CallAdapter<?, ?> nextCallAdapter(@Nullable CallAdapter.Factory skipPast, Type returnType,
      Annotation[] annotations) {
    
    int start = adapterFactories.indexOf(skipPast) + 1;
    //创建callAdapter  遍历这个callAdapter的工厂集合  寻找合适的工厂
    for (int i = start, count = adapterFactories.size(); i < count; i++) {
    //然后通过这个工厂的get方法  还有传入的返回值类型和注解类型  创建我们需要的callAdapter  比如RxJavaCallAdapterFactory
      CallAdapter<?, ?> adapter = adapterFactories.get(i).get(returnType, annotations, this);
      if (adapter != null) {
        return adapter;
      }
    }
```

* createResponseConverter方法
```
//获取接口中网络请求方法的注解类型
 Annotation[] annotations = method.getAnnotations();
        return
        //返回一个converter
        retrofit.responseBodyConverter(responseType, annotations);
        //就是这个方法  传入接口的返回值类型和注解类型
         public <T> Converter<ResponseBody, T> responseBodyConverter(Type type, Annotation[] annotations) {
         //
    return nextResponseBodyConverter(null, type, annotations);
  }
  
  //这个和上述的callAdapter的逻辑有点类似
  public <T> Converter<ResponseBody, T> nextResponseBodyConverter(
    int start = converterFactories.indexOf(skipPast) + 1;
    for (int i = start, count = converterFactories.size(); i < count; i++) {
    //也就是循环数据转换工厂的集合  然后通过传入的返回值类型和注解类型  获取到适合我们的数据转换工厂   然后通过这个转换器responseBodyConver方法获取我们相应的数据转换器  在retrofit中默认的是GsonConverter转换器
      Converter<ResponseBody, ?> converter =
          converterFactories.get(i).responseBodyConverter(type, annotations, this);
      if (converter != null) {
        return (Converter<ResponseBody, T>) converter;
      }
    }

```




