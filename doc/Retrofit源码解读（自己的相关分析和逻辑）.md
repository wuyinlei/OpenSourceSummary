# Retrofit源码解读（自己的相关分析和逻辑）

标签（空格分隔）： Retrofit

---
####写在前面
在没有开源网络请求框架的时候，我们的最简单的一个网络请求是这样的
![](http://ww1.sinaimg.cn/large/006jcGvzgy1fdfdpmbttdj30ny0gq0sw)

* 1、首先build request参数
* 2、因为不能在主线程请求HTTP，所以你得有个Executer或者线程
* 3、enqueue后，通过线程去run你的请求
* 4、得到服务器数据后，callback回调给你的上层。
大概是以上4大步骤，在没有框架的年代，想要做一次请求，是万分痛苦的，你需要自己管理线程切换，需要自己解析读取数据，解析数据成对象，切换回主线程，回调给上层。

```
//在加入baseUrl   convert转换器之后  会调用build()方法
 public Retrofit build() {
      if (baseUrl == null) {
        throw new IllegalStateException("Base URL required.");
      }
      //这个地方虽然是Factory工厂类  但是已经失去了作用 只是简单的在为空的时候创建一个
      //OkHttpClient  也就是表示Retrofit现在只支持OkHttp
      okhttp3.Call.Factory callFactory = this.callFactory;
      if (callFactory == null) {
        callFactory = new OkHttpClient();
      }
      //这个地方会创建Executor  
      Executor callbackExecutor = this.callbackExecutor;
      if (callbackExecutor == null) {
        //这里当为空的时候我们可以看到调用了platform的defaultCallbackExecutor方法
        //在这里最终是创建一个Handler(主线程里面的，然后一直轮询)        //我们可以看到handler.post(r)  可以看到  最终还是通过handler处理runnable   进行网络请求
        callbackExecutor = platform.defaultCallbackExecutor();
      }

      // Make a defensive copy of the adapters and add the default Call adapter.
      List<CallAdapter.Factory> adapterFactories = new ArrayList<>(this.adapterFactories);
      adapterFactories.add(platform.defaultCallAdapterFactory(callbackExecutor));

      // Make a defensive copy of the converters.
      List<Converter.Factory> converterFactories = new ArrayList<>(this.converterFactories);

      return new Retrofit(callFactory, baseUrl, converterFactories, adapterFactories,
          callbackExecutor, validateEagerly);
    }
  }
  
  
    // Make a defensive copy of the adapters and add the default Call adapter.
      List<CallAdapter.Factory> adapterFactories = new ArrayList<>(this.adapterFactories);
      adapterFactories.add(platform.defaultCallAdapterFactory(callbackExecutor));

      // Make a defensive copy of the converters.
      List<Converter.Factory> converterFactories = new ArrayList<>(this.converterFactories);

```

```
//进行逻辑请求
 delegate.enqueue(new Callback<T>() {
        @Override public void onResponse(Call<T> call, final Response<T> response) {
        //callbackExecutor这个就是主线程的handler类似
        //然后执行runnable方法  把结果发送到主线程中  所以我们可以了解到在我们通过异步请求的时候 回调回来的onResponse和onFailure方法是在主线程的，可以直接进行UI处理
          callbackExecutor.execute(new Runnable() {
            @Override public void run() {
              if (delegate.isCanceled()) {
                // Emulate OkHttp's behavior of throwing/delivering an IOException on cancellation.
                callback.onFailure(ExecutorCallbackCall.this, new IOException("Canceled"));
              } else {
                callback.onResponse(ExecutorCallbackCall.this, response);
              }
            }
          });
        }

        @Override public void onFailure(Call<T> call, final Throwable t) {
          callbackExecutor.execute(new Runnable() {
            @Override public void run() {
              callback.onFailure(ExecutorCallbackCall.this, t);
            }
          });
        }
      });
```
```
//最终返回了Retrofit这个对象
return new Retrofit(callFactory, baseUrl, converterFactories, adapterFactories,
          callbackExecutor, validateEagerly);
```

```
 public <T> T create(final Class<T> service) {
    //检测是否是合法的一个service  要是一个接口才行
    Utils.validateServiceInterface(service);
    if (validateEagerly) {
      eagerlyValidateMethods(service);
    }
    //代理
    return (T) Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[] { service },
        new InvocationHandler() {
          private final Platform platform = Platform.get();

          @Override public Object invoke(Object proxy, Method method, Object... args)
              throws Throwable {
            // If the method is a method from Object then defer to normal invocation.
            if (method.getDeclaringClass() == Object.class) {
              return method.invoke(this, args);
            }
            if (platform.isDefaultMethod(method)) {
              return platform.invokeDefaultMethod(method, service, proxy, args);
            }
            //这个就是解析我们自定义的方法上面的方法注解的  比如GET  POST   PUT  DELTET  等方法注解  并且去拼接好接口   ***Adapts an invocation of an interface method into an HTTP call***
            ServiceMethod serviceMethod = loadServiceMethod(method);
            OkHttpCall okHttpCall = new OkHttpCall<>(serviceMethod, args);
            return serviceMethod.callAdapter.adapt(okHttpCall);  //转换成我们需要的一个adapter 然后返回  我们就有了一个Prox的api  通过这个api我们就可以去做一些请求了
          }
        });
  }

```

>api调用方法的时候   动态代理就会生效    首先将Adapts李某的一个interface方法适配成我们的HTTP Call
然后这个时候我们如果没有加入RxJava的话，我们就得到了一个转换好的Call对象  这个时候我们可以调用call.enqueue方法，传入Callback回调  我们就可以通过回调回来的***onResponse和onFailure来处理数据了***


####大致分析
>首先通过Builder模式构建一个Retrofit对象，在build里面我们对retrofit进行了基本的配置，首先配置OkHttpClient,然后🈶Executor(可以从外部传入，当然也可以使用android默认的handler来作为主线程的),然后到后面的CallAdapter,如果我们不传进去,那么就会创建一个默认的defaultCCallAdapterFactory,也就是ExecutorCallAdapterFactory这个类  这个时候我们就创建了callFactory, baseUrl, converterFactories, adapterFactories,callbackExecutor, validateEagerly这么多的对象,然后接着返回一个Retrofit对象,然后这个Retrofit对象通过retrofit.create(api.class),创建一个Api的一个动态代理,当我们调用api的方法的时候,动态代理会去拦截,首先通过ServiceMethod来分析方法上面的注解,来拼成OkHttpClient所需要的请求参数,传递给OkHttpCall,在然后通过serviceMethod.callAdapter.adapt(okHttpCall)将okHttpCall做一次适配,变成ExecutorCallbackCall。这里的这个能做些什么事情呢->就是讲okHttpCall在包装一层,因为okHttpCall在执行的时候是异步的,而我们最终需要的是在主线程的回调，所以他做的就是把子线程的回调最终通过主线程的handler来会带哦到主线程的回调(这也就是为什么我们通过enqueue方法回调之后的结果或者错误已经在主线程了)

###Retrofit套路
>Retrofit  build
Rtrofit create proxy for API interface
API method intercepted by proxy
parse params with ServiceMethod
build HttpCall with params
CallAdapter adapts Call To t
enqueue() waiting for callback

###Retrofit流程图
![](http://ww1.sinaimg.cn/large/006jcGvzgy1fdfbarpqs8j30or0negny)

>流程分析
```
Retrofit.build()
    OkHttpClient
    callbackExecutor
    callAdapter
    convertor
return retrofit


ServiceMethod.build()
    createCallAdapter
    createConvertor
    parseAnnotation
    
    
ExecutorCallbackCall<> callAdapter.adapt(OkHttpCall)
ExecutorCallbackCall.enqueue(new Callback()){
    okhttpCall.enqueue(new Callback()){
        handler.post(new Runnable()){
            callback.onResponse()
        }
    }
}


requestConvertor
okhttp3.Request = okhttpCall.toRequest();
responseConvertor
Retrofit.Response = okhttpCall.toResponse();

Observer<<Result<T>>> callAdapter.adapt(OkHttpCall<>)

```


  [1]: %E6%AD%A4%E5%A4%84%E8%BE%93%E5%85%A5%E9%93%BE%E6%8E%A5%E7%9A%84%E6%8F%8F%E8%BF%B0