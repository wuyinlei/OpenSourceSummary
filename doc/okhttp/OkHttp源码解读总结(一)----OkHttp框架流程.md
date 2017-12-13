# OkHttp源码解读总结(一)--->OkHttp框架流程

标签（空格分隔）： OkHttp源码 学习笔记

---


#### 前言

* 以下的相关知识总结是通过慕课网的相关学习和自己的相关看法,如果有需要的可以去查看一下慕课网的相关教学,感觉还可以。


## 框架流程
OkHttpClient--->Request--->RealCall--->Dispatcher--->interceptors--->RetryAndFollow/Bridge/Cache/Connect/CallServer

### OkHttpClient
* 首先会创建这个OkHttpClient对象,表示我们所有的请求的一个客户端的类,单例创建,只会创建一个

### Request
* 创建Request这个对象,这个对象封装了一些请求报文信息,请求URL地址,请求方法(get/post/put/delete...),还有各种请求头,在他内部我们是通过Builder构建者模式来链式生成Request对象

### RealCall
* 通过调用Request.newCall方法来创建一个Call对象,这个Call对象在我们的OkHttp中的实现类就是RealCall,这个RealCall代表着实际的OkHttp请求,他是连接Request和Response的一个桥梁,有了这个Call,我们才能进行下面的同步或者异步请求操作。

### Dispatcher
* 决定OkHttp的同步或者异步,Dispatcher分发器,在他的内部维护了一个线程池,这个线程池就是用于执行我们的网络请求(同步或者异步),而我们之前创建好的RealCall对象在执行任务的时候,他会将请求添加到Dispatcher里面,在后面的查看源码的时候,我们会看到有三个队列.
```
 /** Ready async calls in the order they'll be run. */
  private final Deque<AsyncCall> readyAsyncCalls = new ArrayDeque<>();

  /** Running asynchronous calls. Includes canceled calls that haven't finished yet. */
  private final Deque<AsyncCall> runningAsyncCalls = new ArrayDeque<>();

  /** Running synchronous calls. Includes canceled calls that haven't finished yet. */
  private final Deque<RealCall> runningSyncCalls = new ArrayDeque<>();
```

### interceptors
* 不管是同步还是异步,在OkHttp内部他都会通过一个叫interceptors(拦截器列)的东西来进行真正的服务器数据获取,会构建一个拦截器列,然后依次执行这个拦截器列当中的每一个拦截器来将我们服务器获得的数据返回,也就是下面涉及到的5个拦截器
* 拦截机制图
![OkHttp的拦截机制][1]

#### 1、RetryAndFollowUpInterceptor
* RetryAndFollowUpInterceptor这个拦截器主要负责两部分逻辑
    * 网络请求失败的时候重试机制
    * 网络请求需要进行重定向的时候会直接发起请求

#### 2、BridgeInterceptor
* BridgeInterceptor,桥接拦截器(主要涉及一些请求前的一些操作)
    * 设置请求内容长度,内容编码
    * gzip压缩
    * 添加Cookie,为他设置其他的报头

#### 3、CacheInterceptor
* CacheInterceptor负责缓存的管理
    * 网络缓存
    * 本地缓存

#### 4、ConnectInterceptor
* ConnectInterceptor为当前的请求找到一个合适连接
* 可以复用原先已有的连接(如果这个连接是可以复用的,就不需要重新创建)
* 还会涉及到连接池的概念

#### 5、CallServerInterceptor
* CallServerInterceptor他的工作就是向我们服务器发起真正的访问请求,然后在接收到服务器返回给我们的Response响应体并返回

### 总结
整个的OkHttp的核心有两大块

* Dispatcher
    * Dispatcher会不断的从request这个队列当中去获取到他所需要的RealCall这个请求,然后根据是否调用缓存,如果有缓存就调用缓存,如果没有缓存就进行下面的ConnectInterceptor/CallServerInterceptor服务器数据的返回,而且Dispatcher还会决定我们是否调用同步的还是异步的请求
* interceptors
    * 不管是同步还是异步,最终都会调用拦截器进行分发 



### 参考
* [Okhttp基本用法和流程分析][2]


  [1]: http://upload-images.jianshu.io/upload_images/1458573-d7375161cda31f32.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/700
  [2]: http://www.jianshu.com/p/db197279f053