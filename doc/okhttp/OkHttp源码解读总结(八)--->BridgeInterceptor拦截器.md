# OkHttp源码解读总结(八)--->BridgeInterceptor拦截器

标签（空格分隔）： OkHttp源码 学习笔记

---


#### 前言

* 以下的相关知识总结是通过慕课网的相关学习和自己的相关看法,如果有需要的可以去查看一下慕课网的相关教学,感觉还可以。

### 官网介绍
```
Bridges from application code to network code. First it builds a network request from a user
 * request. Then it proceeds to call the network. Finally it builds a user response from the network
 * response.
```
主要负责设置内容长度、编码方式、设置gzip压缩、添加请求头、cookie等相关

### 主要方法(主要是添加头部信息)
```
@Override public Response intercept(Chain chain) throws IOException {
    Request userRequest = chain.request();
    Request.Builder requestBuilder = userRequest.newBuilder();

    RequestBody body = userRequest.body();
    if (body != null) {
      //Content-Type
      MediaType contentType = body.contentType();
      if (contentType != null) {
        requestBuilder.header("Content-Type", contentType.toString());
      }

     //响应长度
      long contentLength = body.contentLength();
      if (contentLength != -1) {
        requestBuilder.header("Content-Length", Long.toString(contentLength));
        requestBuilder.removeHeader("Transfer-Encoding");
      } else {
        //转码方式
        requestBuilder.header("Transfer-Encoding", "chunked");
        requestBuilder.removeHeader("Content-Length");
      }
    }
   //host主机
    if (userRequest.header("Host") == null) {
      requestBuilder.header("Host", hostHeader(userRequest.url(), false));
    }

    //Keep-Alive  保持连接状态
    if (userRequest.header("Connection") == null) {
      requestBuilder.header("Connection", "Keep-Alive");
    }

    // If we add an "Accept-Encoding: gzip" header field we're responsible for also decompressing
    // the transfer stream.
    boolean transparentGzip = false;
    if (userRequest.header("Accept-Encoding") == null && userRequest.header("Range") == null) {
      transparentGzip = true;
      requestBuilder.header("Accept-Encoding", "gzip");
    }

    List<Cookie> cookies = cookieJar.loadForRequest(userRequest.url());
    if (!cookies.isEmpty()) {
      requestBuilder.header("Cookie", cookieHeader(cookies));
    }

    if (userRequest.header("User-Agent") == null) {
      requestBuilder.header("User-Agent", Version.userAgent());
    }
    //最重要的还是这个proceed()方法  向服务器发送请求  服务器响应之后返回response
    Response networkResponse = chain.proceed(requestBuilder.build());
    //将网络请求和服务器响应的response  转换为我们用户可以使用的response
    HttpHeaders.receiveHeaders(cookieJar, userRequest.url(), networkResponse.headers());

    Response.Builder responseBuilder = networkResponse.newBuilder()
        .request(userRequest);
    //将网络请求返回的response进行转化
    //transparentGzip==true  支持gzip压缩   Content-Encoding是否支持gzip  判断http头部是否有body
    if (transparentGzip
        && "gzip".equalsIgnoreCase(networkResponse.header("Content-Encoding"))
        && HttpHeaders.hasBody(networkResponse)) {
      //将response的body()输入流转换为GzipSource类型
      GzipSource responseBody = new GzipSource(networkResponse.body().source());
      Headers strippedHeaders = networkResponse.headers().newBuilder()
          .removeAll("Content-Encoding")
          .removeAll("Content-Length")
          .build();
      responseBuilder.headers(strippedHeaders);
      String contentType = networkResponse.header("Content-Type");
      //直接读取
      responseBuilder.body(new RealResponseBody(contentType, -1L, Okio.buffer(responseBody)));
    }

    return responseBuilder.build();
  }

```
上述方法主要设置相关请求头信息、gzip压缩、cookie相关

#### 主要功能
* 1、负责将用户构建的一个Request请求转化为能够进行网络访问的请求
* 2、将这个符合网络请求的Request进行网络请求
* 3、将网络请求回来的响应Response转化为用户可以使用的Response(gzip压缩/解压)


