# OkHttp源码解读总结(十三)--->CallServerInterceptor拦截器

标签（空格分隔）： OkHttp源码 学习笔记

---


#### 前言

* 以下的相关知识总结是通过慕课网的相关学习和自己的相关看法,如果有需要的可以去查看一下慕课网的相关教学,感觉还可以。

### 官网介绍
```
/** This is the last interceptor in the chain. It makes a network call to the server. */

1、发起真正的网络请求
2、返回服务器返回的数据
```

### 主要源码
```
@Override public Response intercept(Chain chain) throws IOException {
    //拦截器链
    RealInterceptorChain realChain = (RealInterceptorChain) chain;
    //HttpCodec对象  source  sink  两个对象 输入和输出  //用于编码request  解码response 
    HttpCodec httpCodec = realChain.httpStream();
    //用于建立网络请求的其他网络组件  
    StreamAllocation streamAllocation = realChain.streamAllocation();
    //
    RealConnection connection = (RealConnection) realChain.connection();
    //网络请求
    Request request = realChain.request();

    long sentRequestMillis = System.currentTimeMillis();

    realChain.eventListener().requestHeadersStart(realChain.call());
    //向socket中写入请求的头部信息
    httpCodec.writeRequestHeaders(request);
    realChain.eventListener().requestHeadersEnd(realChain.call(), request);

    Response.Builder responseBuilder = null;
    if (HttpMethod.permitsRequestBody(request.method()) && request.body() != null) {
      // If there's a "Expect: 100-continue" header on the request, wait for a "HTTP/1.1 100
      // Continue" response before transmitting the request body. If we don't get that, return
      // what we did get (such as a 4xx response) without ever transmitting the request body.
      //询问服务是否可以发送带有请求体的信息
      if ("100-continue".equalsIgnoreCase(request.header("Expect"))) {
        httpCodec.flushRequest();
        realChain.eventListener().responseHeadersStart(realChain.call());
        responseBuilder = httpCodec.readResponseHeaders(true);
      }

      if (responseBuilder == null) {
        // Write the request body if the "Expect: 100-continue" expectation was met.
        realChain.eventListener().requestBodyStart(realChain.call());
        long contentLength = request.body().contentLength();
        CountingSink requestBodyOut =
            new CountingSink(httpCodec.createRequestBody(request, contentLength));
        BufferedSink bufferedRequestBody = Okio.buffer(requestBodyOut);
        //调用request.body().writeTo()方法向socket中写入body信息
        request.body().writeTo(bufferedRequestBody);
        //关闭
        bufferedRequestBody.close();
        realChain.eventListener()
            .requestBodyEnd(realChain.call(), requestBodyOut.successfulCount);
      } else if (!connection.isMultiplexed()) {
        // If the "Expect: 100-continue" expectation wasn't met, prevent the HTTP/1 connection
        // from being reused. Otherwise we're still obligated to transmit the request body to
        // leave the connection in a consistent state.
        streamAllocation.noNewStreams();
      }
    }
    //完成了网络请求的写入操作
    httpCodec.finishRequest();

    if (responseBuilder == null) {
      realChain.eventListener().responseHeadersStart(realChain.call());
      //通过httpCodec的readResponseHeaders  读取response
      responseBuilder = httpCodec.readResponseHeaders(false);
    }
   //通过streamAllocation  request   创建response对象
    Response response = responseBuilder
        .request(request)
        .handshake(streamAllocation.connection().handshake())
        .sentRequestAtMillis(sentRequestMillis)
        .receivedResponseAtMillis(System.currentTimeMillis())
        .build();

    realChain.eventListener()
        .responseHeadersEnd(realChain.call(), response);
    //获取到响应吗
    int code = response.code();
    if (forWebSocket && code == 101) {
      // Connection is upgrading, but we need to ensure interceptors see a non-null response body.
      response = response.newBuilder()
          .body(Util.EMPTY_RESPONSE)
          .build();
    } else {
      //调用httpCodec.openResponseBody(response)创建response
      response = response.newBuilder()
          .body(httpCodec.openResponseBody(response))
          .build();
    }

    if ("close".equalsIgnoreCase(response.request().header("Connection"))
        || "close".equalsIgnoreCase(response.header("Connection"))) {
      //禁止新的流创建  关闭流和关闭connection    
      streamAllocation.noNewStreams();
    }
    //如果code为204  或者205  就抛出异常
    if ((code == 204 || code == 205) && response.body().contentLength() > 0) {
      throw new ProtocolException(
          "HTTP " + code + " had non-zero Content-Length: " + response.body().contentLength());
    }
    //最后返回得到的response
    return response;
  }

```

* 1、调用 httpCodec.writeRequestHeaders(request)向socket对象中写入header信息
* 2、 request.body().writeTo(bufferedRequestBody)向socket当中写入body信息
* 3、 httpCodec.finishRequest()表示网络请求的写入工作完成
* 4、 responseBuilder = httpCodec.readResponseHeaders(false)读取网络请求的头部信息
* 5、通过以下代码逻辑,读取网络请求中的body信息

    ```
    if (forWebSocket && code == 101) {
      // Connection is upgrading, but we need to ensure interceptors see a non-null response body.
      response = response.newBuilder()
          .body(Util.EMPTY_RESPONSE)
          .build();
    } else {
      response = response.newBuilder()
          .body(httpCodec.openResponseBody(response))
          .build();
    }
    ```
    
### OkHttp中的一次网络请求的大致情况
* 1、call对象对请求的封装
* 2、dispatcher分发器对请求进行分发
* 3、getResponseWithInterceptors()方法
    * RetryAndFollowUpInterceptor(重试重定向/失败重连)
    * CacheInterceptor(缓存)
    * BridgeInterceptor(处理请求和响应对象于实际的请求和响应转换/cookie)
    * ConnectInterceptor(负责建立拦截和流对象)
    * CallServerInterceptor(完成最终的网络请求和读取响应)
