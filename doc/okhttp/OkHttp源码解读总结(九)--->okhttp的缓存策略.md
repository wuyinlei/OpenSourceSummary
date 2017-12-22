# OkHttp源码解读总结(九)--->okhttp的缓存策略

标签（空格分隔）： OkHttp源码 学习笔记

---


#### 前言

* 以下的相关知识总结是通过慕课网的相关学习和自己的相关看法,如果有需要的可以去查看一下慕课网的相关教学,感觉还可以。

### 为什么要使用缓存
* 一个优点就是让客户端下一次的网络请求节省更多的时间,更快的展示数据

### 如何开启和使用缓存功能的呢?
```
 new OkHttpClient.Builder()
                .connectTimeout(10000, TimeUnit.MILLISECONDS)
                .readTimeout(10000, TimeUnit.MILLISECONDS)
                .writeTimeout(10000, TimeUnit.MICROSECONDS)
                //直接这样使用  配置Cache   File对象  缓存大小
                .cache(new Cache(new File("cache"),24*1024*1024))
                .build();
```

### Cache.put()方法源码

```
@Nullable CacheRequest put(Response response) {
   //获取到请求方法
    String requestMethod = response.request().method();
    //判断该请求方法是否符合缓存
    if (HttpMethod.invalidatesCache(response.request().method())) {
      try {
        //移除这个请求
        remove(response.request());
      } catch (IOException ignored) {
        // The cache cannot be written.
      }
      return null;
    }
    //非get方法不需要缓存
    if (!requestMethod.equals("GET")) {
      // Don't cache non-GET responses. We're technically allowed to cache
      // HEAD requests and some POST requests, but the complexity of doing
      // so is high and the benefit is low.
      return null;
    }
    //
    if (HttpHeaders.hasVaryAll(response)) {
      return null;
    }
    //当经过上述的逻辑之后 到这一步 证明是可以缓存的  那么久
    //通过传入的response响应   创建Entry对象  就是我们需要写入缓存的   把需要的一些属性  方法  地址  头部 信息  code 等
    Entry entry = new Entry(response);
    //可以看到这里采用的是DiskLruCache缓存策略
    //http://blog.csdn.net/guolin_blog/article/details/28863651  (Android DiskLruCache完全解析，硬盘缓存的最佳方案)
    DiskLruCache.Editor editor = null;
    try {
      //把这个请求url(经过转换MD5加密然后转16进制)作为key
      editor = cache.edit(key(response.request().url()));
      if (editor == null) {
        return null;
      }
      //真正的开始缓存
      entry.writeTo(editor);
      return new CacheRequestImpl(editor);
    } catch (IOException e) {
      abortQuietly(editor);
      return null;
    }
  }

```

entry.writeTo()方法
```
public void writeTo(DiskLruCache.Editor editor) throws IOException {
      //使用的是okio
      BufferedSink sink = Okio.buffer(editor.newSink(ENTRY_METADATA));
      //缓存的请求地址
      sink.writeUtf8(url)
          .writeByte('\n');
      //缓存的请求方法
      sink.writeUtf8(requestMethod)
          .writeByte('\n');
      sink.writeDecimalLong(varyHeaders.size())
          .writeByte('\n');
          //对header头部进行遍历
      for (int i = 0, size = varyHeaders.size(); i < size; i++) {
      //名字
        sink.writeUtf8(varyHeaders.name(i))
            .writeUtf8(": ")
            //value
            .writeUtf8(varyHeaders.value(i))
            .writeByte('\n');
      }
      //缓存http的响应行StatusLine
      sink.writeUtf8(new StatusLine(protocol, code, message).toString())
          .writeByte('\n');
          //响应手部
      sink.writeDecimalLong(responseHeaders.size() + 2)
          .writeByte('\n');
          //头部
      for (int i = 0, size = responseHeaders.size(); i < size; i++) {
        sink.writeUtf8(responseHeaders.name(i))
            .writeUtf8(": ")
            .writeUtf8(responseHeaders.value(i))
            .writeByte('\n');
      }
      //发送时间
      sink.writeUtf8(SENT_MILLIS)
          .writeUtf8(": ")
          .writeDecimalLong(sentRequestMillis)
          .writeByte('\n');
          //响应时间
      sink.writeUtf8(RECEIVED_MILLIS)
          .writeUtf8(": ")
          .writeDecimalLong(receivedResponseMillis)
          .writeByte('\n');
     //判断是否是https请求
      if (isHttps()) {
      //相应的握手  等缓存
        sink.writeByte('\n');
        sink.writeUtf8(handshake.cipherSuite().javaName())
            .writeByte('\n');
        writeCertList(sink, handshake.peerCertificates());
        writeCertList(sink, handshake.localCertificates());
        sink.writeUtf8(handshake.tlsVersion().javaName()).writeByte('\n');
      }
      //关闭
      sink.close();
    }
```
new CacheRequestImpl(editor)
```
CacheRequestImpl实现了CacheRequest接口,主要暴漏给后面需要介绍的CacheInterceptor拦截器  缓存拦截器可以直接根据这个CacheRequest实现类 CacheRequestImpl直接写入和更新缓存数据

    CacheRequestImpl(final DiskLruCache.Editor editor) {
      this.editor = editor;
      //响应主体
      this.cacheOut = editor.newSink(ENTRY_BODY);
      //
      this.body = new ForwardingSink(cacheOut) {
        @Override public void close() throws IOException {
          synchronized (Cache.this) {
            if (done) {
              return;
            }
            done = true;
            writeSuccessCount++;
          }
          super.close();
          editor.commit();
        }
      };
    }
```

#### put()总结
* 1、首先判断缓存请求方法是否是get方法
* 2、如果符合缓存,那么创建一个Entry对象---》用于我们所要包装的缓存信息
* 3、最终使用DiskLruCache进行缓存
* 4、最后通过返回一个CacheRequestImpl对象,这个对象主要用于CacheInterceptor拦截器服务的。


### Cache.get()方法源码
主要从缓存中读取缓存的response
```
@Nullable Response get(Request request) {
    //通过传入的Request对象获取请求地址  并通过key方法获取缓存的key
    String key = key(request.url());
    //缓存快照  记录缓存在特定时刻缓存的内容
    DiskLruCache.Snapshot snapshot;
    
    Entry entry;
    try {
      //通过key值获取value
      snapshot = cache.get(key);
      if (snapshot == null) {
        return null;
      }
    } catch (IOException e) {
      // Give up because the cache cannot be read.
      return null;
    }

    try {
      //通过获取到的这个snapshot获取到Source  最终创建entry对象
      entry = new Entry(snapshot.getSource(ENTRY_METADATA));
    } catch (IOException e) {
      Util.closeQuietly(snapshot);
      return null;
    }
   //通过之前创建好的entry实例获取到我们缓存的response对象
    Response response = entry.response(snapshot);
    //响应和请求是否一一对应  如果不对应 直接返回null
    if (!entry.matches(request, response)) {
      //关闭流
      Util.closeQuietly(response.body());
      return null;
    }
    //最终返回我们缓存的response实例
    return response;
  }
```
entry.response(snapshot)方法
```

    public Response response(DiskLruCache.Snapshot snapshot) {
      String contentType = responseHeaders.get("Content-Type");
      String contentLength = responseHeaders.get("Content-Length");
      //创建request
      Request cacheRequest = new Request.Builder()
          .url(url)
          .method(requestMethod, null)
          .headers(varyHeaders)
          .build();
      return new Response.Builder()
          .request(cacheRequest)
          .protocol(protocol)
          .code(code)
          .message(message)
          .headers(responseHeaders)
          //响应体的读取
          .body(new CacheResponseBody(snapshot, contentType, contentLength))
          .handshake(handshake)
          .sentRequestAtMillis(sentRequestMillis)
          .receivedResponseAtMillis(receivedResponseMillis)
          .build();
    }
```

#### get()方法总结
* 1、根据请求url获取key(MD5解密)
* 2、通过key值获取 DiskLruCache.Snapshot(缓存快照)
* 3、通过获取到的这个snapshot获取到Source  最终创建entry对象
* 4、通过entry和snapshot(缓存快照)获取缓存的response并返回

>这里所总结的Cache的get和put的相关知识,主要是为了为CacheInterceptor这个缓存拦截器做铺垫。

