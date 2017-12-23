# OkHttp源码解读总结(十一)--->ConnectInterceptor拦截器

标签（空格分隔）： OkHttp源码 学习笔记

---


#### 前言

* 以下的相关知识总结是通过慕课网的相关学习和自己的相关看法,如果有需要的可以去查看一下慕课网的相关教学,感觉还可以。

### 连接池的概念

#### 1、ConnectInterceptor拦截
打开与服务器之间的连接,正式开启okhttp的网络请求
```
@Override public Response intercept(Chain chain) throws IOException {
    //
    RealInterceptorChain realChain = (RealInterceptorChain) chain;
    Request request = realChain.request();
    //StreamAllocation创建  用来建立http请求所需要的所有网络组件  在RetryAndFollowUpInterceptor重试连接器中 只是初始化了 但是没有使用  他只是把创建好的StreamAllocation进行了向下传递   最终由ConnectInterceptor拦截器获取和使用
    StreamAllocation streamAllocation = realChain.streamAllocation();

    // We need the network to satisfy this request. Possibly for validating a conditional GET.
    boolean doExtensiveHealthChecks = !request.method().equals("GET");
    //通过streamAllocation实例创建HttpCodec对象  用于编码request和解码response这个
    HttpCodec httpCodec = streamAllocation.newStream(client, chain, doExtensiveHealthChecks);
    //通过streamAllocation的connection()方法获取RealConnection对象   用于进行实际的网络io传输的 
    RealConnection connection = streamAllocation.connection();
    //调用proceed()方法  设置好了的拦截器
    return realChain.proceed(request, streamAllocation, httpCodec, connection);
  }
```

#### 总结
* 1、ConnectInterceptor获取Interceptor传过来的StreamAllocation对象,通过StreamAllocation.newStream()这个对象获取HttpCodec对象,这个HttpCodec对象用于编码request和解码response
* 2、将刚才创建好的用于网络IO的RealConnection对象,以及对于与服务器交互最为关键的HttpCodec等对象传递给后面的拦截器
#### StreamAllocation.newStream()方法
```
 public HttpCodec newStream(
      OkHttpClient client, Interceptor.Chain chain, boolean doExtensiveHealthChecks) {
    int connectTimeout = chain.connectTimeoutMillis();
    int readTimeout = chain.readTimeoutMillis();
    int writeTimeout = chain.writeTimeoutMillis();
    boolean connectionRetryEnabled = client.retryOnConnectionFailure();

    try {
    //创建RealConnection对象   进行实际的网络连接
      RealConnection resultConnection = findHealthyConnection(connectTimeout, readTimeout,
          writeTimeout, connectionRetryEnabled, doExtensiveHealthChecks);
      //创建HttpCodec对象
      HttpCodec resultCodec = resultConnection.newCodec(client, chain, this);
     //同步代码块  返回HttpCodec这个对象
      synchronized (connectionPool) {
        codec = resultCodec;
        return resultCodec;
      }
    } catch (IOException e) {
      throw new RouteException(e);
    }
  }
```
findHealthyConnection(connectTimeout, readTimeout,
          writeTimeout, connectionRetryEnabled, doExtensiveHealthChecks)方法
```
private RealConnection findHealthyConnection(int connectTimeout, int readTimeout,
      int writeTimeout, boolean connectionRetryEnabled, boolean doExtensiveHealthChecks)
      throws IOException {
    while (true) {
      //获取RealConnection
      RealConnection candidate = findConnection(connectTimeout, readTimeout, writeTimeout,
          connectionRetryEnabled);

      // If this is a brand new connection, we can skip the extensive health checks.
      synchronized (connectionPool) {
      //如果这个数量为0  循环结束  
        if (candidate.successCount == 0) {
          return candidate;
        }
      }

      // Do a (potentially slow) check to confirm that the pooled connection is still good. If it
      // isn't, take it out of the pool and start again.
      if (!candidate.isHealthy(doExtensiveHealthChecks)) {
       //进行销毁资源
        noNewStreams();
        //循环
        continue;
      }

      return candidate;
    }
  }
```

findConnection(int connectTimeout, int readTimeout, int writeTimeout,
      boolean connectionRetryEnabled)方法
```
  private RealConnection findConnection(int connectTimeout, int readTimeout, int writeTimeout,
      boolean connectionRetryEnabled) throws IOException {
    boolean foundPooledConnection = false;
    RealConnection result = null;
    Route selectedRoute = null;
    Connection releasedConnection;
    Socket toClose;
    synchronized (connectionPool) {
      if (released) throw new IllegalStateException("released");
      if (codec != null) throw new IllegalStateException("codec != null");
      if (canceled) throw new IOException("Canceled");

      // Attempt to use an already-allocated connection. We need to be careful here because our
      // already-allocated connection may have been restricted from creating new streams.
      //复用
      releasedConnection = this.connection;
      toClose = releaseIfNoNewStreams();
      //不为空
      if (this.connection != null) {
        // We had an already-allocated connection and it's good.
        //把这个result进行返回   如果可以复用就直接返回了
        result = this.connection;
        releasedConnection = null;
      }
      if (!reportedAcquired) {
        // If the connection was never reported acquired, don't report it as released!
        releasedConnection = null;
      }
     //如果不能复用
      if (result == null) {
      //从连接池当中获取一个连接
        // Attempt to get a connection from the pool.
        Internal.instance.get(connectionPool, address, this, null);
        if (connection != null) {
          foundPooledConnection = true;
          result = connection;
        } else {
          selectedRoute = route;
        }
      }
    }
    closeQuietly(toClose);

    if (releasedConnection != null) {
      eventListener.connectionReleased(call, releasedConnection);
    }
    if (foundPooledConnection) {
      eventListener.connectionAcquired(call, result);
    }
    if (result != null) {
      // If we found an already-allocated or pooled connection, we're done.
      return result;
    }

    // If we need a route selection, make one. This is a blocking operation.
    boolean newRouteSelection = false;
    if (selectedRoute == null && (routeSelection == null || !routeSelection.hasNext())) {
      newRouteSelection = true;
      routeSelection = routeSelector.next();
    }

    synchronized (connectionPool) {
      if (canceled) throw new IOException("Canceled");

      if (newRouteSelection) {
        // Now that we have a set of IP addresses, make another attempt at getting a connection from
        // the pool. This could match due to connection coalescing.
        List<Route> routes = routeSelection.getAll();
        for (int i = 0, size = routes.size(); i < size; i++) {
          Route route = routes.get(i);
          Internal.instance.get(connectionPool, address, this, route);
          if (connection != null) {
            foundPooledConnection = true;
            result = connection;
            this.route = route;
            break;
          }
        }
      }

      if (!foundPooledConnection) {
        if (selectedRoute == null) {
          selectedRoute = routeSelection.next();
        }

        // Create a connection and assign it to this allocation immediately. This makes it possible
        // for an asynchronous cancel() to interrupt the handshake we're about to do.
        route = selectedRoute;
        refusedStreamCount = 0;
        result = new RealConnection(connectionPool, selectedRoute);
        acquire(result, false);
      }
    }

    // If we found a pooled connection on the 2nd time around, we're done.
    if (foundPooledConnection) {
      eventListener.connectionAcquired(call, result);
      return result;
    }

    // Do TCP + TLS handshakes. This is a blocking operation.
    //当获取到这个链接之后 就进行实际的网络连接
    result.connect(
        connectTimeout, readTimeout, writeTimeout, connectionRetryEnabled, call, eventListener);
    routeDatabase().connected(result.route());
   //
    Socket socket = null;
    synchronized (connectionPool) {
      reportedAcquired = true;

      // Pool the connection.
      //把result放入连接池中
      Internal.instance.put(connectionPool, result);

      // If another multiplexed connection to the same address was created concurrently, then
      // release this connection and acquire that one.
      if (result.isMultiplexed()) {
      //创建socket
        socket = Internal.instance.deduplicate(connectionPool, address, this);
        //当前链接
        result = connection;
      }
    }
    //关闭socket
    closeQuietly(socket);

    eventListener.connectionAcquired(call, result);
    return result;
  }


尝试获取connection  如果可以复用 那么就直接复用  如果不能复用 就从连接池中获取一个connection  

```
* 1、创建一个RealConnection对象
* 2、选择不同的链接方式
    * socket链接
    * 隧道链接
* 3、CallServerInterceptor这个拦截器完成整个的okhttp的网络请求
