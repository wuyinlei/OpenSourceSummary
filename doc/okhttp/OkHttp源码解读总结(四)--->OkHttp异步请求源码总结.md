# OkHttp源码解读总结(四)--->OkHttp异步请求源码总结

标签（空格分隔）： OkHttp源码 学习笔记

---


#### 前言

* 以下的相关知识总结是通过慕课网的相关学习和自己的相关看法,如果有需要的可以去查看一下慕课网的相关教学,感觉还可以。

>上一节已经总结了同步请求源码,接下来查看异步,因为同步和异步的差别只在于Call之后的execute()和enqueue()方法,因此前三步骤这里不再赘述。

### enqueue()异步请求
call是个接口,因此enqueue这个方法要到实现类RealCall中进行
```
 @Override 
 public void enqueue(Callback responseCallback) {
    //锁
    synchronized (this) {
      //是否已经执行过  
      if (executed) throw new IllegalStateException("Already Executed");
      executed = true;
    }
    //捕捉堆栈信息
    captureCallStackTrace();
    //开启监听
    eventListener.callStart(this);
    //通过将传入的Callback对象传入AsyncCall()里面创建一个AsyncCall对象实例  因此当我们通过client.dispatcher获取到Dispatcher这个分发器 然后传入到刚创建好的Runnable这个实例,然后调用enqueue()方法进行异步网络请求
    client.dispatcher().enqueue(new AsyncCall(responseCallback));
  }
```
client.dispatcher()
```
 //返回Dispatcher对象
 public Dispatcher dispatcher() {
    return dispatcher;  //这个就是在okhttpclient的构造方法的第一行就已经实例化好的
  }
```
AsyncCall(responseCallback)
```
class AsyncCall extends NamedRunnable   //我们可以看出,这个AsyncCall继承于NamedRunnable,查看NamedRunnable实现Runnable,因此AsyncCall就是一个Runnable实例
```

enqueue(AsyncCall call)

```
 //同步锁  传入创建好的异步Runnable
 synchronized void enqueue(AsyncCall call) {
    //判断实际的运行请求数是否小于允许的最大的请求数量(64)  并且共享主机的正在运行的调用的数量小于同时最大的相同Host的请求数(5)
    if (runningAsyncCalls.size() < maxRequests && runningCallsForHost(call) < maxRequestsPerHost) {
     //如果都符合 那么就把这个请求添加到正在执行的异步请求队列当中
      runningAsyncCalls.add(call);
      //然后通过线程池去执行这个请求call
      executorService().execute(call);
    } else {
      //否则的话 在就绪(等待)异步请求队列当中添加
      readyAsyncCalls.add(call);
    }
  }
```

 executorService()(**Dispatcher类中的方法**)
 
```
  //单例  返回ExecutorService这个线程池对象  同步
  public synchronized ExecutorService executorService() {
    if (executorService == null) {
      //虽然这个地方定义了最大的线程池的数量是Integer.MAX_VALUE  但是我们知道上面对请求数量有了限制(64个)
      executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
          new SynchronousQueue<Runnable>(), Util.threadFactory("OkHttp Dispatcher", false));
    }
    return executorService;
  }
```
关于线程池的推荐
[深入理解java线程池—ThreadPoolExecutor][1]


当创建好ExecutorService这个线程池对象之后,就需要调用execute(call)执行方法执行Runnable,也就是执行AsyncCall里面的run()方法,我们去查找AsyncCall里面的run方法,可以看到没有这个方法,却有一个execute()方法,而这个方法是复写的父类的方法,我们查看NamedRunnable这个类,可以发现有个run方法
```
  @Override public final void run() {
    //获取到当先线程的名字()
    String oldName = Thread.currentThread().getName();
    //赋值
    Thread.currentThread().setName(name);
    try {
      //执行  这个是抽象类 也就是刚才在AsyncCall看到的execute()方法
      execute();
    } finally {
      Thread.currentThread().setName(oldName);
    }
  }
```
RealCall中的execute()方法
```
@Override protected void execute() {
      boolean signalledCallback = false;
      try {
      //拦截器链
        Response response = getResponseWithInterceptorChain();
        //重定向/重试是否取消
        if (retryAndFollowUpInterceptor.isCanceled()) {
          signalledCallback = true;
          //callback的onFailure()返回  在call.enqueue()里面传进去的
          responseCallback.onFailure(RealCall.this, new IOException("Canceled"));
        } else {
          //如果成功  返回结果
          signalledCallback = true;
          responseCallback.onResponse(RealCall.this, response);
        }
      } catch (IOException e) {
       //网络请求失败的操作
        if (signalledCallback) {
          // Do not signal the callback twice!
          Platform.get().log(INFO, "Callback failure for " + toLoggableString(), e);
        } else {
          eventListener.callFailed(RealCall.this, e);
          responseCallback.onFailure(RealCall.this, e);
        }
      } finally {
        //
        client.dispatcher().finished(this);
      }
    }
```
finished(AsyncCall call)方法
```
 void finished(AsyncCall call) {
    finished(runningAsyncCalls, call, true);
  }
```
finished(runningAsyncCalls, call, true)方法
```
private <T> void finished(Deque<T> calls, T call, boolean promoteCalls) {
    int runningCallsCount;
    Runnable idleCallback;
    synchronized (this) {
     //1、调用calls.remove()方法进行删除正在请求的异步线程
      if (!calls.remove(call)) throw new AssertionError("Call wasn't in-flight!");
      //2、调用promoteCalls()方法调整整个请求队列
      if (promoteCalls) promoteCalls();
      //3、重新计算正在执行的线程的数量
      runningCallsCount = runningCallsCount();
      idleCallback = this.idleCallback;
    }

    if (runningCallsCount == 0 && idleCallback != null) {
      idleCallback.run();
    }
  }
```
最终不管是成功还是失败,都会返回到我们复写的Callback方法里面
```
requestCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                //当请求出错的时候  会回调到这个方法中
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                //要注意这个地方是子线程,okhttp并没有给我们把响应的数据转换到主线程中,因此我们在这个地方更新UI的时候
                //是会报错的  需要我们手动的转换到主线程  然后才进行数据的解析和UI更新   在之前的retrofit的讲解中
                //也稍微有所涉及  那就是retrofit已经帮我们把异步的这个请求  返回来的callback已经切换到了主线程
                //得益于retrofit里面的Executorer这个回调执行器
                if (response.isSuccessful()){
                    String string = response.body().string();
                }
            }
        });
```

### 总结
* 1、判断当前call
  * 这个请求只能被执行一次,如果已经请求过了,就会抛出异常
* 2、通过传递进来的Callback封装成一个AsyncCall(Runnable)对象
* 3、获取Dispatcher对象并执行enqueue()异步请求
    * 如果这个AsyncCall请求符合条件(判断实际的运行请求数是否小于允许的最大的请求数量(64)  并且共享主机的正在运行的调用的数量小于同时最大的相同Host的请求数(5))   才会添加到执行异步请求队列,然后通过线程池进行异步请求
    * 否则就把这个AsyncCall请求添加到就绪(等待)异步请求队列当中
    * 这个Dispatcher持有一个正在执行的请求队列/就绪(等待)执行的请求队列,还有一个就是线程池。这三个来完整的处理异步请求操作。
  


  [1]: http://www.jianshu.com/p/ade771d2c9c0
