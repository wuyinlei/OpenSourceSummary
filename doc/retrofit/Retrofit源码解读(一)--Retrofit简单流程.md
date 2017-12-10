# Retrofit源码解读(一)--Retrofit简单流程

标签（空格分隔）：Retrofit源码

---

### Retrofit
不是网络请求框架，而是对网络请求框架的封装,是整个框架的门面类,整个入口,可以通过这个方法进行我们的请求的配置,配置的方式就是通过里面的一个内部类Builder,使用构建者模式创建,里面有个重要的变量就是ServiceMethod

### ServiceMethod
对应我们写好的接口类中的方法,通过动态代理的模式,通过这个可以将我们定义好的方法(包含方法里面的参数,注解)转换成一个一个的http请求。同时ServiceMethod可以帮我们解析方法中的注解,生成我们需要的Request对象。在ServiceMethod这个类中生成了三个重要的工厂类**CallAdapter工厂**、**Converter工厂**

* CallAdapter工厂
默认把网络请求封装成okhttpcall,这个作用就是将okhttpcall转换成适合不同平台使用的解析(比如java8,ios,android平台)
* Converter工厂
是用于生产数据转换,默认情况下是把okhttp返回的response对象转换成我们的java对象进行使用。而在Retrofit里面默认是使用Gson来解析数据
* CallFactory工厂
用于创建Call请求类,http请求会抽象封装成call类,同时表示请求已经准备好了,随时可以运行

这个时候通过这几个工厂类的创建和配合,可以创建出OkHttpCall请求,可以进行同步或者异步请求,可以看出,Retrofit只是对okhttpcall的一层封装,底层还是通过okhttp进行的网络请求。

当通过okhttpcall进行请求,对之前ServiceMethod生成的一些地址,参数,请求之后(同步或者异步都行),返回结果之后,就会通过之前创建好的CallAdapter工厂,把okhttpcall转换成不同平台使用的。

然后通过Converter进行数据转换,最终得到一个response对象,然后最后通过callbackExecutor进行线程切换(子线程请求数据,主线程更新UI),***当然了这个只是针对异步请求,同步请求是不需要这个的。***

### 简单的使用
#### 请求流程
首先确定的一点就是,Retrofit是一个网络请求框架的封装工具类,而不是一个网络请求框架,他底层使用的是okhttp这个网络请求框架,只是在okhttp的基础上进行了更深层次的封装,更加简单易用。所以流程就是
```seq
App->Retrofit: 发送请求

Retrofit-->App: 数据(解析好)

Retrofit->OkHttp: 我要请求数据

OkHttp-->Retrofit: 请求好数据返回

OkHttp->Server: 后台,给我数据

Server-->OkHttp: 呐,数据给你
```
* App应用程序通过Retrofit请求网络,实际上是使用Retrofit接口层封装请求参数和HTTP方式,之后交由OkHttp完成后续的网络请求工作
* 在服务端返回数据之后,OkHttp将原始的结果交给Retrofit,Retrofit根据用户的需求对数据进行解析,然后进行相关逻辑操作

#### 代码演示
##### 例子一
* Retrofit turns your HTTP API into a Java interface.
```
public interface GitHubService {
    // @GET  表示get请求方法
    // users/{user}/repos  这个就是和baseurl进行拼接 是个完整的请求地址
    //{user}  这个大括号,表示里面的参数是动态的可以更换的  具体的值就是对应@Path("user") String user  这个传递进入的user
  @GET("users/{user}/repos")
  Call<List<Repo>> listRepos(@Path("user") String user);
}
```
* The Retrofit class generates an implementation of the GitHubService interface.
```
Retrofit retrofit = new Retrofit.Builder()
    .baseUrl("https://api.github.com/")
    .build();

GitHubService service = retrofit.create(GitHubService.class);

```
* Each Call from the created GitHubService can make a synchronous or asynchronous HTTP request to the remote webserver.

```
Call<List<Repo>> repos = service.listRepos("octocat");
```

```
repos.enqueue(new Callback(){
    
    @Override
    public void onFailure(Call call,IOException e){
            //请求出错进行的逻辑
    }
    
    @Override
    public void onResponse(Call call ,Response response) throws IOException{
        //在这里进行的是成功数据只会的相关请求
    }
})
```

[上述的代码演示就是官网的简单示例][1]  [点击这里][2]

##### 例子二
1、添加网络权限
```
 <uses-permission android:name="android.permission.INTERNET"/>
```
2、创建一个接受的返回类型

```
class HttpResult<T> {

    private int count;
    private int err;
    private int total;
    private int page;
    private int refresh;

    //用来模仿Data
    private T items;

}

class TestBean{
    private String format;

    private long published_at;

    private String content;
    private String state;
}


 public class User {

        /**
         * avatar_updated_at : 1418571809
         * uid : 13846208
         * last_visited_at : 1390853782
         * created_at : 1390853782
         * state : active
         * last_device : android_2.6.4
         * role : n
         * login : ---切随缘
         * id : 13846208
         * icon : 20141215074328.jpg
         */

        private int avatar_updated_at;
        private int uid;
        private int last_visited_at;
        private int created_at;
        private String state;
        private String last_device;
        private String role;
        private String login;
        private int id;
        private String icon;
}
```
3、创建服务接口
```

public interface TestInterface {
    //每个方法参数都需要进行注解标志 要不然就会报错  这个在后面的分析详细逻辑的时候会讲
    @GET("article/list/latest?page=1")
    Call<HttpResult<List<TestBean>>> getQiuShiJsonString();

}

```
4、创建Retrofit实例
```
 public static Retrofit getRetrofit() {

        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(10, TimeUnit.SECONDS);


        return new Retrofit.Builder()
                .client(builder.build())
                //设置请求网络地址的url(基地址  这个地址一定要"/"结尾  要不会出现问题)
                // public Builder baseUrl(HttpUrl baseUrl) {
      //checkNotNull(baseUrl, "baseUrl == null");
      //List<String> pathSegments = baseUrl.pathSegments();
     // if (!"".equals(pathSegments.get(pathSegments.size() - 1))) {
      //  throw new IllegalArgumentException("baseUrl must end in /: " + baseUrl);
     // }
     // this.baseUrl = baseUrl;
     // return this;
    //}
                .baseUrl("http://m2.qiushibaike.com/")  
                .addConverterFactory(GsonConverterFactory.create()) //设置数据解析器  默认使用的Gson
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create()) //设置支持RxJava转换器
                .build();

    }
```
5、创建网络请求的接口实例
```
   TestInterface service = getRetrofit().create(TestInterface.class);
```
6、通过接口实例创建call方法
```
 Call<HttpResult<List<TestBean>>> qiuShiJson = service.getQiuShiJsonString();
```
7、通过call执行异步(或者同步方法)
```
 qiuShiJson.enqueue(new Callback<HttpResult<List<TestBean>>>() {
            @Override
            public void onResponse(Call<HttpResult<List<TestBean>>> call, Response<HttpResult<List<TestBean>>> response) {
                Log.d("TestRetrofit", "请求回来的数据");
                if (response.isSuccessful()){
                    HttpResult<List<TestBean>> body = response.body();
                    Log.d("TestRetrofit", "body.getSubjects().size():" + body.getSubjects().size());
                }
            }

            @Override
            public void onFailure(Call<HttpResult<List<TestBean>>> call, Throwable t) {
                Log.d("TestRetrofit", "请求数据失败,失败的原因" + t.getMessage());
            }
        });

```
### 项目地址

[开源项目Retrofit源码查看和总结][3]
[开源项目Retrofit源码查看和总结][3]
[开源项目Retrofit源码查看和总结][3]


  [1]: http://square.github.io/retrofit/
  [2]: http://square.github.io/retrofit/
  [3]: https://github.com/wuyinlei/OpenSourceSummary