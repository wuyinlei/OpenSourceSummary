# OkHttp源码解读总结(五)--->OkHttp核心调度器Dispatcher类源码总结

标签（空格分隔）： OkHttp源码 学习笔记

---


#### 前言

* 以下的相关知识总结是通过慕课网的相关学习和自己的相关看法,如果有需要的可以去查看一下慕课网的相关教学,感觉还可以。

>okhttp是如何实现同步和异步的请求呢？

### Dispatcher

#### Dispatcher的作用

* 发送的同步/异步请求都会在Dispatcher中管理其状态

#### 到底什么是Dispatcher?

* Dispatcher的作用是维护请求的状态
* 并维护一个线程池,用于执行请求。

### Dispatcher源码

#### 成员变量
```
 //最大同时请求数
 private int maxRequests = 64;
 //同时最大的相同Host的请求数
 private int maxRequestsPerHost = 5;
 private @Nullable Runnable idleCallback;
 //线程池  维护执行请求和等待请求  
 private @Nullable ExecutorService executorService;
 //异步的等待队列
 private final Deque<AsyncCall> readyAsyncCalls = new ArrayDeque<>();
 //异步的执行对列
 private final Deque<AsyncCall> runningAsyncCalls = new ArrayDeque<>();
 //同步的执行队列
 private final Deque<RealCall> runningSyncCalls = new ArrayDeque<>();
```

#### 异步请求为什么需要两个队列?
我们可以知道,对于异步的请求队列,有一个是异步的执行队列和一个异步的等待队列。
这个可以理解为生产者和消费者模型

* Dispatcher-->生产者(默认主线程)
* ExecutorService-->消费者
    *  readyAsyncCalls-->异步缓存队列
    *  runningAsyncCalls-->异步执行队列

因此对于下方的这个流程图就比较容易理解了,当客户端通过call.enqueue(runnable)方法之后,这个是Dispatcher就会进行分发,当判断当前的最大请求数是否还在允许范围内&最大主机也是允许范围内,那么久会把这个请求扔到**异步执行队列**中,然后开辟新的线程进行网络请求,当不符合上述规则的时候,就把这个请求扔到**异步等待队列**当中,直到之前的**异步执行队列**中有空余的线程就也就是调用promoteCall()删除执行队列,就会从**异步等待队列**中获取需要执行的网络请求。

![image.png](http://upload-images.jianshu.io/upload_images/1316820-f5ef9b8c73520248.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

#### 
对于同步的请求,当调用execute()方法之后
```
 synchronized void executed(RealCall call) {
    runningSyncCalls.add(call);
  }
```
这个方法也很简单,就是执行把这个Call请求添加到同步执行队列当中。

对于异步的请求,当调用enqueue()方法之后,最终执行的代码
```
 synchronized void enqueue(AsyncCall call) {
    if (runningAsyncCalls.size() < maxRequests && runningCallsForHost(call) < maxRequestsPerHost) {
      //当当前的AsyncCall可以立即执行
      runningAsyncCalls.add(call);
      executorService().execute(call);
    } else {
      //不能立即执行  需要等待
      readyAsyncCalls.add(call);
    }
  }

```

#### ExecutorService
```
 public synchronized ExecutorService executorService() {
    if (executorService == null) {
      //这个在创建线程池的时候,设定核心线程池个数为0  最大的线程数(但是由于有其他限制,这个也不是无线的创建线程) 非核心线程的KeepLive空闲时间为60s   任务队列为SynchronousQueue 也就是当没有请求的时候 过60s  就会清空这个线程池
      executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
          new SynchronousQueue<Runnable>(), Util.threadFactory("OkHttp Dispatcher", false));
    }
    return executorService;
  }
```
这个主要就是调度**正在执行的队列**和**等待执行队列**,当正在执行队列有完成的,就会把等待队列中优先级高的调整添加到正在执行的队列当中,同时把他从等待执行队列中移除。保证网络请求的高效运转。

#### Call执行完肯定需要在runningAsyncCalls队列中移除这个线程

* 那么readyAsyncCalls队列中的线程在什么时候才会被执行呢?
    因为异步的请求,需要我们把之前封装好的AsyncCall(runnable),这个入队。当然我们可以查看这个AsyncCall的execute()方法,按理说应该是run()方法,但是在他的上层(NamedRunnable)里面对run()方法进行了相关逻辑,出来了一个execute()方法,我们可以看到AsyncCall的execute()方法执行的finally语句最终会执行一句 `client.dispatcher().finished(this);`,如果跟进去看源码的时候,最终会调用下面的代码块
```
 finished(runningAsyncCalls, call, true);

   //首先把当前的请求从正在执行的请求队列中删除
   if (!calls.remove(call)) throw new AssertionError("Call wasn't in-flight!");

//也就是promoteCalls始终为true(当是异步请求的时候)
 if (promoteCalls) promoteCalls();

//调整异步请求队列
 private void promoteCalls() {
    //判断当前正在运行的异步请求的数量是否是允许最大数量
    if (runningAsyncCalls.size() >= maxRequests) return; // Already running max capacity.
    //如果还能进行异步请求(还有余额)  判断等待队列是否为空  如果是空的 那么直接返回
    if (readyAsyncCalls.isEmpty()) return; // No ready calls to promote.
    //循环正在等待(就绪)执行队列
    for (Iterator<AsyncCall> i = readyAsyncCalls.iterator(); i.hasNext(); ) {
      //获取到AsyncCall实例
      AsyncCall call = i.next();
      //判断所有运行的主机是否小于最大的限制
      if (runningCallsForHost(call) < maxRequestsPerHost) {
        //如果都符合  那么就把这个AsyncCall从等待队列中删除
        i.remove();
        //把这个AsyncCall(等待的)添加到正在执行的请求队列中
        runningAsyncCalls.add(call);
        //通过线城市ExecutorService执行请求
        executorService().execute(call);
      }

      if (runningAsyncCalls.size() >= maxRequests) return; // Reached max capacity.
    }
  }

```
