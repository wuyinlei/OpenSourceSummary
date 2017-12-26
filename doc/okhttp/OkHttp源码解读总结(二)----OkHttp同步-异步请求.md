# OkHttp源码解读总结(二)--->OkHttp同步/异步请求

标签（空格分隔）： OkHttp源码 学习笔记

---


#### 前言

* 以下的相关知识总结是通过慕课网的相关学习和自己的相关看法,如果有需要的可以去查看一下慕课网的相关教学,感觉还可以。


## OkHttp同步请求
### 同步请求简单步骤
```
  /**
     * 构建OkHttpClient对象
     *
     * @return OkHttpClient
     */
    public OkHttpClient getOkHttpClient() {

        return new OkHttpClient.Builder()
                .connectTimeout(10000, TimeUnit.MILLISECONDS)
                .readTimeout(10000, TimeUnit.MILLISECONDS)
                .writeTimeout(10000, TimeUnit.MICROSECONDS)
                .build();
    }

    /**
     * 同步请求
     */
    public void synRequest() {

        Request request = new Request.Builder().url("http://www.baidu.com")
                .get()
                .build();

        Call requestCall = getOkHttpClient().newCall(request);

        try {
            Response response = requestCall.execute();
            if (response.isSuccessful()) {
                String json = response.body().string();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

```
#### 1、获取OkHttpClient
```
new OkHttpClient.Builder()
                .connectTimeout(10000, TimeUnit.MILLISECONDS)
                .readTimeout(10000, TimeUnit.MILLISECONDS)
                .writeTimeout(10000, TimeUnit.MICROSECONDS)
                .build()
```
代表着okhttp请求的客户端类,很多的功能需要这个客户端类进行转发或者实现,而他的创建主要有两种方法:

* 第一种方法
    * new OkHttpClient()
* 第二种方法
    * new OkHttpClient.Builder().build()来创建 
    * 因为要考虑要其他请求
        * 网络复杂(连接超时/读取超时...)
        * 需要设置其他参数(拦截器/分发器等...)
        
#### 2、获取Request请求对象
```
 Request request = new Request.Builder()
 .url("http://www.baidu.com")  //
                .get()
                .build();
```
代表着请求的报文信息,比如请求的方法、请求头、请求地址,这个Request也是通过构建者模式来创建。

#### 3、获取okhttp3.Call对象
```
 Call requestCall = getOkHttpClient().newCall(request);
```
这个Call对象,就相当于实际的OkHttp请求,也就是可以把他理解为是Request请求和Response响应的一个桥梁,通过client的newCall()方法,传入我们之前创建好的request对象。要注意,这个之前和同步请求和异步请求没什么区别,在接下来的步骤就是实现的同步或者异步请求的逻辑了。也就是前三步骤只是为了获取实际的请求Call。

#### 4、Call对象的execute()同步请求方法
```
  //同步请求
  Response response = requestCall.execute();
  //响应成功
  if (response.isSuccessful()) {
      //通过响应的body().string()方法获取返回回来的json数据(也可以是其他类型的数据(XML类型)  这个需要和服务器端商量好)
      String json = response.body().string();
  }
```
通过execute()方法来获取响应报文的读取,Response顾名思义就是响应体,其中包含了响应头,相应实体信息,同步和异步请求最大的区别就是这个第四步骤,因为异步请求调用的则是enqueue()方法,传入CallBack进行数据的成功和失败逻辑

#### 同步请求总结
* 1、创建OkHttpClient和Requet对象
* 2、将Request封装成Call对象
* 3、调用Call的execute()方法同步请求

>同步请求发送之后,就会进入阻塞状态,直到收到响应。而且也只有同步请求会阻塞,异步请求是新开的线程做网络请求.

如下图:不管事同步还是异步请求,最终都会经过`getResponseWithInterceptorChain()方法` 这个方法  构造一个拦截器链,经过不同的拦截器,最后返回数据。(后面会介绍)

![image.png](http://upload-images.jianshu.io/upload_images/1316820-312538cea47800ad.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

### 异步请求
* 前三步骤和同步请求类似,不同的是第四个步骤也就是下方的代码逻辑,主要是call的enqueue()方法,传入相应的CallBack进行数据返回之后的返回和错误处理。
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

>因此可以看到,同步请求和异步请求的差异在于第四步,也就是execute()同步方法和enqueue()异步方法。

