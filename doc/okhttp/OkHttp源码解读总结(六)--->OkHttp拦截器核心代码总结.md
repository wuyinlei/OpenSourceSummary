# OkHttp源码解读总结(六)--->OkHttp拦截器核心代码总结

标签（空格分隔）： OkHttp源码 学习笔记

---


#### 前言

* 以下的相关知识总结是通过慕课网的相关学习和自己的相关看法,如果有需要的可以去查看一下慕课网的相关教学,感觉还可以。

### 简单回顾
* 同步请求就是执行请求的操作是阻塞式,知道HTTP响应返回
* 异步请求就类似于非阻塞的请求(新开一个工作线程),它的执行结果一般都是通过接口回调的方式告知调用者,在回调中进行相关逻辑

### 官网拦截器简介
拦截器是OkHttp中提供的一种强大的机制,他可以实现网络监听、请求以及响应重写、请求失败重试等功能(不区分同步和异步)
![官网拦截器介绍][1]

* Application Interceptors
* Network Interceptors

下述是okhttp提供给我们的拦截器

![image.png](http://upload-images.jianshu.io/upload_images/1316820-d971507023856158.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)


### 通过同步请求走进代码
```
 //主要是这个方法  返回的是网络响应 
 Response result = getResponseWithInterceptorChain();
 
  //可以看出以下的代码,就是创建所谓的拦截器链,然后通过依次执行每一个不同功能的拦截器,获取服务器端的返回response
  // Build a full stack of interceptors.
    List<Interceptor> interceptors = new ArrayList<>();
    //添加用户自定义的拦截器  这个是在创建okhttpclient的时候有个addInterceptors()方法
    interceptors.addAll(client.interceptors());
    interceptors.add(retryAndFollowUpInterceptor);
    interceptors.add(new BridgeInterceptor(client.cookieJar()));
    interceptors.add(new CacheInterceptor(client.internalCache()));
    interceptors.add(new ConnectInterceptor(client));
    if (!forWebSocket) {
      interceptors.addAll(client.networkInterceptors());
    }
    interceptors.add(new CallServerInterceptor(forWebSocket));
    //创建了Interceptor.Chain这个对象  把我们之前创建好的拦截器集合传入
    Interceptor.Chain chain = new RealInterceptorChain(interceptors, null, null, null, 0,
        originalRequest, this, eventListener, client.connectTimeoutMillis(),
        client.readTimeoutMillis(), client.writeTimeoutMillis());
    //通过调用proceed()方法完成请求
    return chain.proceed(originalRequest);
  }

```
chain.proceed(originalRequest)

```
@Override public Response proceed(Request request) throws IOException {
    return proceed(request, streamAllocation, httpCodec, connection);
  }
  
  在proceed()方法中的比较重要的一句代码
  
   // Call the next interceptor in the chain.
   //index + 1  表述 如果下面的请求如果要访问的话 需要从下一个拦截器进行访问  而不是从当前拦截器进行访问  这样做也是把拦截器做成一个拦截器链
    RealInterceptorChain next = new RealInterceptorChain(interceptors, streamAllocation, httpCodec,
        connection, index + 1, request, call, eventListener, connectTimeout, readTimeout,
        writeTimeout);
    //获取到最后一个拦截器(这个index是通过index+1之后的成员变量)    
    Interceptor interceptor = interceptors.get(index);
    //然后把新创建的拦截器进行执行
    Response response = interceptor.intercept(next);
```
其实也就是

* 1、创建一系列拦截器,并将其放入一个拦截器list中(包含应用程序拦截器和客户端创建的拦截器)
* 2、创建一个拦截器链RealInterceptorChain,并执行拦截器链的proceed()方法  其实这个方法核心就是创建下一个拦截器  然后调用intercept()方法  这样就构成了拦截器链。

#### intercept()方法


```
  //这段代码是RetryAndFollowUpInterceptor中intercept方法
  //可以看到proceed()方法就是创建下一个拦截器  也就是如何实现连接器链的机制  其实也就是可以这样理解--->每一个okhttp请求都是一个一个的拦截器执行他自己的intercept方法的过程
  response = realChain.proceed(request, streamAllocation, null, null);
```

##### okhttp拦截器的核心逻辑过程

* 1、在发起请求前对request进行处理
* 2、调用下一个拦截器,获取response(形成链条)
* 3、对response进行处理,返回给上一个拦截器




  [1]: https://raw.githubusercontent.com/wiki/square/okhttp/interceptors@2x.png
