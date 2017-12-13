# OkHttp源码解读总结(三)--->OkHttp同步请求源码解析

标签（空格分隔）： OkHttp源码 学习笔记

---


#### 前言

* 以下的相关知识总结是通过慕课网的相关学习和自己的相关看法,如果有需要的可以去查看一下慕课网的相关教学,感觉还可以。


### 第一步创建的OkHttpClient客户端
创建这个OkHttpClient并设置相关的参数,需要用到他的内部类的Builder的build()方法。首先我们看下这个builder的构造方法(无参的构造方法)
```
 public Builder() {
 
      //主要的几个参数
        
      //dispatcher对象  http请求的分发器 主要是作用于异步请求是需要直接处理还是进行缓存等待(缓存)
      dispatcher = new Dispatcher();
    
      //连接池   客户端和服务器之间的可以看做一个Connection  而每一个Connection我们都会把他放在这个连接池中  进行统一管理  (1)如果URL相同的时候可以选择复用  （2）进行相关设置,是保持连接还是选择以后复用管理
      connectionPool = new ConnectionPool();
     
      //默认的超时相关时间
      connectTimeout = 10_000;
      readTimeout = 10_000;
      writeTimeout = 10_000;
    }
```

### 第二步创建Request请求报文信息类
这个Request对象的创建也是一个建造者模式来构建的。通过链式调用指定请求方法,指定头部,请求参数等等设置
```
 //这个比较简单  
 public Builder() {
      设置请求方法为GET请求
      this.method = "GET";
      //创建了一个Headers的内部类 用于保存头部信息
      this.headers = new Headers.Builder();
    }
```

```
 
 public Request build() {
      if (url == null) throw new IllegalStateException("url == null");
      //可以看到这个build方法就是把当前的Builder对象传递到Request里面  创建Request对象  也就是说 通过这个build()方法之后,就能把我们已经配置好的Builder的中的请求方式,请求地址,头部 都赋值给我们的Request对象
      return new Request(this);
    }
    
    
    所以可以看下Request这个构造方法  我们可以看到在构造方法中,通过传递进来的Builder这个实例来进行相关变量的赋值
    
     Request(Builder builder) {
    this.url = builder.url; //网络地址
    this.method = builder.method; //请求方式
    this.headers = builder.headers.build(); //头部
    this.body = builder.body;  //body
    this.tag = builder.tag != null ? builder.tag : this;
  }

```
### 3、创建Http请求的实际Call对象
```
 Call requestCall = getOkHttpClient().newCall(request);
    
  @Override 
  public Call newCall(Request request) {
    //因为Call是一个接口   他的实际操作都是在他的实现类中进行 也就是RealCall这个实现类
    return RealCall.newRealCall(this, request, false /* for web socket */);
  }
```
Call请求的实现类RealCall
```
 static RealCall newRealCall(OkHttpClient client, Request originalRequest, boolean forWebSocket) {
    // Safely publish the Call instance to the EventListener.
    //创建了实现类的对象  通过okhttpclient  request
    RealCall call = new RealCall(client, originalRequest, forWebSocket);
    call.eventListener = client.eventListenerFactory().create(call);
    return call;
  }
  
  
 RealCall的构造方法
 
  private RealCall(OkHttpClient client, Request originalRequest, boolean forWebSocket) {
    //持有传入进来的两个实例
    this.client = client;
    this.originalRequest = originalRequest;
    this.forWebSocket = forWebSocket;
    //创建一个重定向拦截器  以后会进行讲解
    this.retryAndFollowUpInterceptor = new RetryAndFollowUpInterceptor(client, forWebSocket);
  }
  
  不管是同步还是异步,都是通过newCall()这个方法创建的RealCall对象来进行相应的操作
```

### 4、execute()方法进行同步请求
```
  Response response = requestCall.execute();
  
  RealCall的实现类覆写的这个execute()方法
  
   @Override public Response execute() throws IOException {
    //同步
    synchronized (this) {
      //判断这个是否被执行过  一个http请求只能执行一次  如果已经执行了 那么就会抛出异常  
      if (executed) throw new IllegalStateException("Already Executed");
      executed = true;
    }
    //捕捉异常堆栈信息
    captureCallStackTrace();
    //监听事件开启
    eventListener.callStart(this);
    try {
      //核心代码
      client.dispatcher().executed(this);
      
      //当把请求call放置到同步请求队列当中  进行请求之后  我们通过getResponseWithInterceptorChain()这个方法来获取相应Response  这个之后再总结(拦截器)
      Response result = getResponseWithInterceptorChain();
      if (result == null) throw new IOException("Canceled");
      return result;
    } catch (IOException e) {
      eventListener.callFailed(this, e);
      throw e;
    } finally {
      //结束
      client.dispatcher().finished(this);
      
     
    }
  }
  
```
client.dispatcher()
```
 public Dispatcher dispatcher() {
    return dispatcher;
  }
```

client.dispatcher().executed(this)
```
 /** Running synchronous calls. Includes canceled calls that haven't finished yet. */
  private final Deque<RealCall> runningSyncCalls = new ArrayDeque<>();  //这个是在Dispatcher类中定义的 同步请求队列  可以看出 还定义的有
   /** Ready async calls in the order they'll be run. */
   //异步就绪队列
  private final Deque<AsyncCall> readyAsyncCalls = new ArrayDeque<>();

  /** Running asynchronous calls. Includes canceled calls that haven't finished yet. */
  //异步执行队列
  private final Deque<AsyncCall> runningAsyncCalls = new ArrayDeque<>();

 synchronized void executed(RealCall call) {
    //添加到同步请求队列
    runningSyncCalls.add(call);
  }
```

补充
![image.png](http://upload-images.jianshu.io/upload_images/1316820-e852cf38751ec596.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)


 client.dispatcher().finished(this)方法;
 
```
  void finished(RealCall call) {
    finished(runningSyncCalls, call, false);
  }
  
   private <T> void finished(Deque<T> calls, T call, boolean promoteCalls) {
    //把我们当前正在执行的请求传递过来  然后把这个请求从同步请求队列中进行移除(如果能移除)
    int runningCallsCount;
    Runnable idleCallback;
    synchronized (this) {
      if (!calls.remove(call)) throw new AssertionError("Call wasn't in-flight!");
      //同步不走  因为始终传入的是false
      if (promoteCalls) promoteCalls();
      //计算目前还在进行的请求
      runningCallsCount = runningCallsCount();
      idleCallback = this.idleCallback;
    }
    //如果正在请求的数为0  没有可运行的请求   并且idleCallback不为空
    if (runningCallsCount == 0 && idleCallback != null) {   
      //run方法
      idleCallback.run();
    }
  }
  
  //返回正在执行的异步和同步请求总和
   public synchronized int runningCallsCount() {
    return runningAsyncCalls.size() + runningSyncCalls.size();
  }

```
>可以看出这个Dispatcher分发器在同步请求中做的很简单,那就是保存和移除同步请求。对于异步请求,Dispatcher就需要做的好多工作了。

