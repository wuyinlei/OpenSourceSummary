# OkHttp源码解读总结(十)--->CacheInterceptor拦截器

标签（空格分隔）： OkHttp源码 学习笔记

---


#### 前言

* 以下的相关知识总结是通过慕课网的相关学习和自己的相关看法,如果有需要的可以去查看一下慕课网的相关教学,感觉还可以。

>前面总结了Cache的get(读)和put(取)方法,HTTP的缓存的工作是通过一个叫CacheInterceptor拦截器来完成的 

### 源码解读
```
@Override public Response intercept(Chain chain) throws IOException {
    //1、如果配置缓存  则读取缓存，不保证存在
    Response cacheCandidate = cache != null
        ? cache.get(chain.request())
        : null;
    //当前时间
    long now = System.currentTimeMillis();
    //缓存策略  内部维护了一个request和一个response  内部可以指定 是通过网络(request)  还是通过缓存(response) 还是通过两者一起 去获取response
    //通过CacheStrategy.Factory的工厂类的get方法获取这个缓存策略   (1)标注
    CacheStrategy strategy = new CacheStrategy.Factory(now, chain.request(), cacheCandidate).get();
    //获取request
    Request networkRequest = strategy.networkRequest;
    Response cacheResponse = strategy.cacheResponse;
    //判断是否有缓存
    if (cache != null) {
      //当有缓存的时候  更新缓存指标  这个我们要实现的话 是实现的okhttp3.cache里面的方法
      cache.trackResponse(strategy);
    }

    if (cacheCandidate != null && cacheResponse == null) {
      //当缓存不符合要求的时候 关闭
      closeQuietly(cacheCandidate.body()); // The cache candidate wasn't applicable. Close it.
    }

    // If we're forbidden from using the network and the cache is insufficient, fail.
    //如果当前未使用网络  并且缓存不可以使用
    if (networkRequest == null && cacheResponse == null) {
      //通过构建者模式创建一个Response响应  抛出504错误
      return new Response.Builder()
          .request(chain.request())
          .protocol(Protocol.HTTP_1_1)
          .code(504)
          .message("Unsatisfiable Request (only-if-cached)")
          .body(Util.EMPTY_RESPONSE)
          .sentRequestAtMillis(-1L)
          .receivedResponseAtMillis(System.currentTimeMillis())
          .build();
    }

    // If we don't need the network, we're done.
    //如果有缓存 但是不能使用网络  直接返回缓存结果
    if (networkRequest == null) {
      return cacheResponse.newBuilder()
          .cacheResponse(stripBody(cacheResponse))
          .build();
    }
   //
    Response networkResponse = null;
    try {
      //通过调用proceed()方法进行网络的数据获取 并且交给下一个拦截器进行获取(ConnectInterceptor拦截器)
      networkResponse = chain.proceed(networkRequest);
    } finally {
      // If we're crashing on I/O or otherwise, don't leak the cache body.
      if (networkResponse == null && cacheCandidate != null) {
        closeQuietly(cacheCandidate.body());
      }
    }

    // If we have a cache response too, then we're doing a conditional get.
    if (cacheResponse != null) {
      //判断响应码是否是HTTP_NOT_MODIFIED = 304   从缓存中读取数据
      if (networkResponse.code() == HTTP_NOT_MODIFIED) {
        Response response = cacheResponse.newBuilder()
            .headers(combine(cacheResponse.headers(), networkResponse.headers()))
            .sentRequestAtMillis(networkResponse.sentRequestAtMillis())
            .receivedResponseAtMillis(networkResponse.receivedResponseAtMillis())
            .cacheResponse(stripBody(cacheResponse))
            .networkResponse(stripBody(networkResponse))
            .build();
        networkResponse.body().close();

        // Update the cache after combining headers but before stripping the
        // Content-Encoding header (as performed by initContentStream()).
        cache.trackConditionalCacheHit();
        cache.update(cacheResponse, response);
        return response;
      } else {
        closeQuietly(cacheResponse.body());
      }
    }

    Response response = networkResponse.newBuilder()
        .cacheResponse(stripBody(cacheResponse))
        .networkResponse(stripBody(networkResponse))
        .build();

    if (cache != null) {
      //头部是否有响应体  是否有缓存
      if (HttpHeaders.hasBody(response) && CacheStrategy.isCacheable(response, networkRequest)) {
        // Offer this request to the cache.
        CacheRequest cacheRequest = cache.put(response);
        //缓存到本地
        return cacheWritingResponse(cacheRequest, response);
      }
      //判断这个方法是否是无效的缓存方法
      if (HttpMethod.invalidatesCache(networkRequest.method())) {
        try {
        // 删除
          cache.remove(networkRequest);
        } catch (IOException ignored) {
          // The cache cannot be written.
        }
      }
    }

    return response;
  }

```
CacheStrategy.Factory.get()方法
```
 public CacheStrategy get() {
      //
      CacheStrategy candidate = getCandidate();

      if (candidate.networkRequest != null && request.cacheControl().onlyIfCached()) {
        // We're forbidden from using the network and the cache is insufficient.
        return new CacheStrategy(null, null);
      }

      return candidate;
    }
```
getCandidate()方法
```
 /** Returns a strategy to use assuming the request can use the network. */
    private CacheStrategy getCandidate() {
      // No cached response.  没有response
      if (cacheResponse == null) {
      //重新创建一个网络请求   
        return new CacheStrategy(request, null);
      }

      // Drop the cached response if it's missing a required handshake.
      //请求是否是https  并且这个请求是否经历过握手(handshake()操作)
      if (request.isHttps() && cacheResponse.handshake() == null) {
        //重新进行网络请求
        return new CacheStrategy(request, null);
      }

      // If this response shouldn't have been stored, it should never be used
      // as a response source. This check should be redundant as long as the
      // persistence store is well-behaved and the rules are constant.
      //判断这个响应是否要进行存储
      if (!isCacheable(cacheResponse, request)) {
        //如果不存储 则重新网络请求
        return new CacheStrategy(request, null);
      }
     //
      CacheControl requestCaching = request.cacheControl();
      //如果我们指定了不进行网络缓存   并且请求是可选择
      if (requestCaching.noCache() ||  hasConditions(request)) {
        //重新网络请求
        return new CacheStrategy(request, null);
      }
     //
      CacheControl responseCaching = cacheResponse.cacheControl();
      //
      if (responseCaching.immutable()) {
       
        return new CacheStrategy(null, cacheResponse);
      }
     
      long ageMillis = cacheResponseAge();
      long freshMillis = computeFreshnessLifetime();
     
      if (requestCaching.maxAgeSeconds() != -1) {
        freshMillis = Math.min(freshMillis, SECONDS.toMillis(requestCaching.maxAgeSeconds()));
      }

      long minFreshMillis = 0;
      if (requestCaching.minFreshSeconds() != -1) {
        minFreshMillis = SECONDS.toMillis(requestCaching.minFreshSeconds());
      }

      long maxStaleMillis = 0;
      if (!responseCaching.mustRevalidate() && requestCaching.maxStaleSeconds() != -1) {
        maxStaleMillis = SECONDS.toMillis(requestCaching.maxStaleSeconds());
      }

      if (!responseCaching.noCache() && ageMillis + minFreshMillis < freshMillis + maxStaleMillis) {
        Response.Builder builder = cacheResponse.newBuilder();
        if (ageMillis + minFreshMillis >= freshMillis) {
          //添加请求头
          builder.addHeader("Warning", "110 HttpURLConnection \"Response is stale\"");
        }
        long oneDayMillis = 24 * 60 * 60 * 1000L;
        if (ageMillis > oneDayMillis && isFreshnessLifetimeHeuristic()) {
          builder.addHeader("Warning", "113 HttpURLConnection \"Heuristic expiration\"");
        }
        //最终返回
        return new CacheStrategy(null, builder.build());
      }

      // Find a condition to add to the request. If the condition is satisfied, the response body
      // will not be transmitted.
      String conditionName;
      String conditionValue;
      if (etag != null) {
        conditionName = "If-None-Match";
        conditionValue = etag;
      } else if (lastModified != null) {
        conditionName = "If-Modified-Since";
        conditionValue = lastModifiedString;
      } else if (servedDate != null) {
        conditionName = "If-Modified-Since";
        conditionValue = servedDateString;
      } else {
        return new CacheStrategy(request, null); // No condition! Make a regular request.
      }

      Headers.Builder conditionalRequestHeaders = request.headers().newBuilder();
      Internal.instance.addLenient(conditionalRequestHeaders, conditionName, conditionValue);

      Request conditionalRequest = request.newBuilder()
          .headers(conditionalRequestHeaders.build())
          .build();
      return new CacheStrategy(conditionalRequest, cacheResponse);
    }

```


