# OkHttp源码解读总结(七)--->RetryAndFollowUpInterceptor拦截器

标签（空格分隔）： OkHttp源码 学习笔记

---


#### 前言

* 以下的相关知识总结是通过慕课网的相关学习和自己的相关看法,如果有需要的可以去查看一下慕课网的相关教学,感觉还可以。

### 主要作用
```
//官网介绍
This interceptor recovers from failures and follows redirects as necessary. It may throw an
 * {@link IOException} if the call was canceled.
 
 主要负责失败重连,并不是所有的网络请求都会去失败重连的,它是有一个范围的,在okhttp内部进行检测网络请求的异常和响应码的判断,如果都在限制范围内,那么就可以进行失败重连。
```

#### intercept()方法源码
```
@Override public Response intercept(Chain chain) throws IOException {
    //获取request对象
    Request request = chain.request();
    RealInterceptorChain realChain = (RealInterceptorChain) chain;
    //获取当前请求的call对象
    Call call = realChain.call();
    //监听器
    EventListener eventListener = realChain.eventListener();
    //创建一个StreamAllocation对象  用来建立http请求的网络组件   用于获取服务端的conn和数据传输的输入输出流  在这里并没有用到
    //// 三个参数分别对应，全局的连接池仅对http／2有用，连接线路Address, 堆栈对象
    streamAllocation = new StreamAllocation(client.connectionPool(), createAddress(request.url()),
        call, eventListener, callStackTrace);

    int followUpCount = 0;
    Response priorResponse = null;
    while (true) {
      if (canceled) {
        streamAllocation.release();
        throw new IOException("Canceled");
      }

      Response response;
      boolean releaseConnection = true;
      try {
        //执行下一个拦截器 即BridgeInterceptor
        response = realChain.proceed(request, streamAllocation, null, null);
        releaseConnection = false;
      } catch (RouteException e) {
        // The attempt to connect via a route failed. The request will not have been sent.
        //如果有异常 判断是否需要恢复
        if (!recover(e.getLastConnectException(), false, request)) {
          throw e.getLastConnectException();
        }
        releaseConnection = false;
        continue;
      } catch (IOException e) {
        // An attempt to communicate with a server failed. The request may have been sent.
        boolean requestSendStarted = !(e instanceof ConnectionShutdownException);
        if (!recover(e, requestSendStarted, request)) throw e;
        releaseConnection = false;
        continue;
      } finally {
        // We're throwing an unchecked exception. Release any resources.
        if (releaseConnection) {
          streamAllocation.streamFailed(null);
          streamAllocation.release();
        }
      }

      // Attach the prior response if it exists. Such responses never have a body.
      if (priorResponse != null) {
        response = response.newBuilder()
            .priorResponse(priorResponse.newBuilder()
                    .body(null)
                    .build())
            .build();
      }

      Request followUp = followUpRequest(response);

      if (followUp == null) {
        if (!forWebSocket) {
          streamAllocation.release();
        }
        return response;
      }

      closeQuietly(response.body());
      //对重试的次数进行判断  MAX_FOLLOW_UPS = 20 次
      if (++followUpCount > MAX_FOLLOW_UPS) {
        //释放这个对象  
        streamAllocation.release();
        throw new ProtocolException("Too many follow-up requests: " + followUpCount);
      }

      if (followUp.body() instanceof UnrepeatableRequestBody) {
        streamAllocation.release();
        throw new HttpRetryException("Cannot retry streamed HTTP body", response.code());
      }

      if (!sameConnection(response, followUp.url())) {
        streamAllocation.release();
        streamAllocation = new StreamAllocation(client.connectionPool(),
            createAddress(followUp.url()), call, eventListener, callStackTrace);
      } else if (streamAllocation.codec() != null) {
        throw new IllegalStateException("Closing the body of " + response
            + " didn't close its backing stream. Bad interceptor?");
      }

      request = followUp;
      priorResponse = response;
    }
  }
```
* 1、创建StreamAllocation对象
* 2、调用RealInterceptorChainrealChain.proceed(request, streamAllocation, null, null);  进行网络请求和调用下一个拦截器
* 3、根据异常和响应结果判断是否重连pan
* 4、调用下一个拦截器,对response进行处理,返回给上一个拦截器


### 继续下一个拦截器

>将继续执行下一个连接器BridgeInterceptor




