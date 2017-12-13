# Retrofit源码解读(五)--okhttpCall和adapt方法

标签（空格分隔）： Retrofit源码 学习笔记

---
#### 前言

* 以下的相关知识总结是通过慕课网的相关学习和自己的相关看法,如果有需要的可以去查看一下慕课网的相关教学,感觉还可以。

### 内容
上一节总结了ServiceMethod的类和相关作用,接下来就总结一下下面的两行方法.
```
OkHttpCall<Object> okHttpCall = new OkHttpCall<>(serviceMethod, args);
            return serviceMethod.callAdapter.adapt(okHttpCall);
```

#### OkHttpCall（实现了okhttp的Call）
成员变量
```
  //ServiceMethod对象 用来表述网络请求参数信息
  private final ServiceMethod<T, ?> serviceMethod;
  //表述网络接口参数
  private final @Nullable Object[] args;
 //该请求是否可以被取消  状态标志位
  private volatile boolean canceled;
  //okhttp3.Call   实际进行网络请求的类
  @GuardedBy("this")
  private @Nullable okhttp3.Call rawCall;
  //异常  出现异常的处理类
  @GuardedBy("this")
  private @Nullable Throwable creationFailure; // Either a RuntimeException or IOException.
  //标志位  用于异步方法执行逻辑使用
  @GuardedBy("this")
  private boolean executed;

```

构造函数
```
//ServiceMethod   请求接口参数
OkHttpCall(ServiceMethod<T, ?> serviceMethod, @Nullable Object[] args) {
    this.serviceMethod = serviceMethod;
    this.args = args;
  }
```
OkHttpCall就是Retrofit这个库对于OkHttp的一个封装,请求网络的时候,不管事同步还是异步,都是通过OkHttpCall进行的请求

#### serviceMethod.callAdapter.adapt(okHttpCall);
通过调用adapt这个方法就是用来进行适配的,通过传入创建好的okhttpcall来获取他的返回值
我们可以看到callAdapter是属于serviceMethod里面的参数,这个参数的赋值在serviceMethod的build的时候进行的赋值。adapt也就是通过适配器模式,转换成我们需要的一些请求,比如当我们addCallAdapterFactory(RxJavaCallAdapterFactory.create())的时候,我们想要的就是通过RxJava来处理请求返回的response,那么通过adapt这个方法就可以转换成Observable这个类型,来让我们操作。也就是可以对于不同平台的其他类型。

所以当通过crete这个方法之后,我们就创建了 `TestInterface service = getRetrofit().create(TestInterface.class);`这个接口实例,然后通过接口实例的

 service.getQiuShiJsonString()

但是有个疑问,那就是接口实例调用方法,这个时候我们可以想到的就是前面提到的动态代理,荣光Proxy.newProxyInstance进行拦截,然后调用InvocationHandler中的invoke()方法来进行实际的操作,最终我们会返回一个OkHttpcall类型的call对象来进行实际的网络请求,所以说我们的service.getQiuShiJsonString()这个请求,就是通过动态代理返还给我们的call对象来进行网络请你去,这个okhttpcall又是对okhttp的网络请求的封装,所以也就是通过okhttp来进行的同步和异步请求。

### Retrofit请求
* 同步请求OkHttpCall.execute()
* 异步请求OkHttpCall.enqueue(callback)

>经过之前的分析,我们可以知道Retrofit的Call其实是对于OkHttpCall的请求的封装,因此Retrofit的同步和异步请求就是调用OkHttpCall的请求来进行的同步和异步

#### 同步VS异步
* 回调执行器
    * 整个的同步和异步请求比较类似,区别在于回调执行器,异步会把数据返回之后交给execute(回调执行器来做),由Executor来指定相应的线程去完成不同的操作 

#### Retrofit同步请求
* 1、ParameterHandler进行请求方法的参数解析
* 2、根据ServiceMethod这个对象创建OkHttp的Request对象
    * 有了这个request对象我们才能调用okhttp进行网络请求
    * 而且ServiceMethod包含了我们所有的网络请求的基本信息(当前请求和已经换成的请求)
    * 而且这个ServiceMethod有缓存,使用的是ConcurrentHashMap,进行缓存是为了高效运行
* 3、通过okhttpCall发送网络请求
* 4、调用converter进行数据解析(默认是GsonConverter)

```
 qiuShiJson.execute();  //同步请求方法
 
 
 @Override public Response<T> execute() throws IOException {
    //创建okhttp3 里面的Call对象  来请求数据
    okhttp3.Call call;
    //加锁
    synchronized (this) {
     //是否执行的标志位  
      if (executed) throw new IllegalStateException("Already executed.");
      executed = true;
      call = rawCall;
      if (call == null) {
        try {
        //创建一个新的okhttpcall
          call = rawCall = createRawCall();
        } catch (IOException | RuntimeException e) {
          creationFailure = e;
          throw e;
        }
    }
    //当canceled为true的时候 直接取消请求
    if (canceled) {
      call.cancel();
    }
    //接下来分析这个parseResponse()方法  因为call.execute()这个call其实就是okhttp的call,然后这个请求就已经交给了okhttp进行请求,这里暂不做相关分析
    return parseResponse(call.execute());
  }
  
  //创建okhttp3.Call
   private okhttp3.Call createRawCall() throws IOException {
    Request request = serviceMethod.toRequest(args);
    //创建okhttp call
    okhttp3.Call call = serviceMethod.callFactory.newCall(request);
   //直接返回
    return call;
  }
  //上述方法就是把我们网络请求中的参数,调用ParameterHandler对象来进行参数的解析,然后在通过serviceMethod.toRequest()方法来生成Request这个对象,最后把这个Request对象通过serviceMethod的CallFactory工厂的newCall()方法创建我们的实际请求的OkHttp.Call对象
 

```

##### parseResponse(call.execute())方法
```
Response<T> parseResponse(okhttp3.Response rawResponse) throws IOException {
    //获取到ResponseBody
    ResponseBody rawBody = rawResponse.body();
    
    // Remove the body's source (the only stateful object) so we can pass the response along.
    rawResponse = rawResponse.newBuilder()
        .body(new NoContentResponseBody(rawBody.contentType(), rawBody.contentLength()))
        .build();
    //返回码  暂不解析。。。
   
   
    ExceptionCatchingRequestBody catchingBody = new ExceptionCatchingRequestBody(rawBody);
    //主要是这个toResponse()方法     最终也就是调用我们传入的GsonConverter转换器  转换成我们需要的数据类型
    /** Builds a method return value from an HTTP response body. 
  //R toResponse(ResponseBody body) throws IOException //{
  //  return responseConverter.convert(body);
  //}
      T body = serviceMethod.toResponse(catchingBody);
      //返回成功的response  完成了整个的response解析
      return Response.success(body, rawResponse);
    }
  }
```
上述就是retrofit的同步请求的相关解析。其实简单来说,retrofit就是通过接口的注解和参数,把我们的http请求进行了包装，然后呢我们在retrofit使用的时候,然后在使用的时候我们只需要把重点放在接口的创建上,通过接口来配置方法和参数,其他都是通过他的内部来进行逻辑处理,他内部也是通过动态代理将客户端写好的接口中的方法转换成ServiceMethod对象,然后通过这个ServiceMethod对象来获取到我们需要的信息(数据转换器/网络适配器等、、),最终网络底层的请求还是交给了okhttp来进行。
    
    
#### Retrofit异步请求
异步请求的相关原理基本和Retrofit的同步请求类似,关于那4步骤可以看Retrofit的同步请求刚开始那4条。主要的区别就是在于异步的异步回调

```
@Override public void enqueue(final Callback<T> callback) {
    //okhttpcall
    okhttp3.Call call;
    //错误
    Throwable failure;

    synchronized (this) {
    //和同步类似
      if (executed) throw new IllegalStateException("Already executed.");
      executed = true;

      call = rawCall;
      failure = creationFailure;
        try {
        //创建rawCall
          call = rawCall = createRawCall();
        } catch (Throwable t) {
          failure = creationFailure = t;
        }
    }
    //错误相关逻辑处理
    if (failure != null) {
      callback.onFailure(this, failure);
      return;
    }
    //是否取消请求
    if (canceled) {
      call.cancel();
    }
    //这个才是真正的异步请求
    call.enqueue(new okhttp3.Callback() {
      @Override public void onResponse(okhttp3.Call call, okhttp3.Response rawResponse)
          throws IOException {
        Response<T> response;
        try {
        //这个是正确请求返回数据只会  解析response
          response = parseResponse(rawResponse);
        } catch (Throwable e) {
          callFailure(e);
          return;
        }
        //调用成功的逻辑  回调执行器
        callSuccess(response);
      }

      @Override public void onFailure(okhttp3.Call call, IOException e) {
        try {
        //失败的时候  回调执行器
          callback.onFailure(OkHttpCall.this, e);
        } catch (Throwable t) {
          t.printStackTrace();
        }
      }
      //失败的逻辑处理
      private void callFailure(Throwable e) {
        try {
        //这个就是callback  在我们自己的实现的onFailure里面进行异常的处理
          callback.onFailure(OkHttpCall.this, e);
        } catch (Throwable t) {
          t.printStackTrace();
        }
      }

      private void callSuccess(Response<T> response) {
        try {
        //成功的返回 这个会在我们异步请求中  复写这个onResponse方法来进行  数据逻辑处理,第一个参数是OkHttpCall   第二个参数就是response这个  也就是如果我们接受的是HttpResult<List<TestBean>>>这个类型  那么我们返回的也是这个类型  Response<HttpResult<List<TestBean>>> response  这个response就是我们的最终解析后的数据类型
          callback.onResponse(OkHttpCall.this, response);
        } catch (Throwable t) {
          t.printStackTrace();
        }
      }
    });
  }
```





