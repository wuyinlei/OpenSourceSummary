# Retrofit源码解读(六)--Retrofit中的设计模式

标签（空格分隔）： Retrofit源码 学习笔记

---
#### 前言

* 以下的相关知识总结是通过慕课网的相关学习和自己的相关看法,如果有需要的可以去查看一下慕课网的相关教学,感觉还可以。

### 构建者(Builder)设计模式
* Retrofit实例创建
```
 Retrofit retrofit = new Retrofit.Builder()
                .client(builder.build())
                .addConverterFactory(GsonConverterFactory.create()) 
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create()) //设置支持RxJava转换器
                .build();
```
* ServiceMethod实例构建
```
ServiceMethod result = new ServiceMethod.Builder<>(this, method).build();
```

#### Builder好处
* 将一个复杂对象的构建与它的表示分离，使得同样的构建过程可以创建不同的表示
* 类图

![建造者模式类图][1]

#### 使用场景
* 创建复杂对象的算法独立于组成对象的部件
* 同一个创建过程需要有不同的内部表象的产品对象


### 工厂方法模式
```
abstract class Factory {
    /**
     * Returns a call adapter for interface methods that return {@code returnType}, or null if it
     * cannot be handled by this factory.
     */
    public abstract @Nullable CallAdapter<?, ?> get(Type returnType, Annotation[] annotations,
        Retrofit retrofit);

    /**
     * Extract the upper bound of the generic parameter at {@code index} from {@code type}. For
     * example, index 1 of {@code Map<String, ? extends Runnable>} returns {@code Runnable}.
     */
    protected static Type getParameterUpperBound(int index, ParameterizedType type) {
      return Utils.getParameterUpperBound(index, type);
    }

    /**
     * Extract the raw class type from {@code type}. For example, the type representing
     * {@code List<? extends Runnable>} returns {@code List.class}.
     */
    protected static Class<?> getRawType(Type type) {
      return Utils.getRawType(type);
    }
  }
  
  可以看出,当实现get方法之后,会返回不同类型的CallAdapter
  比如RxJavaCallAdapterFactory中返回的是RxJavaCallAdapter这个对象,默认的DefaultCallAdapterFactory中返回的直接是CallAdapter<Object, Call<?>>,也就是说如果想要返回不同的callAdapter类型,只需要我们继承CallAdapter.Factory,然后实现里面的get方法并进行逻辑改造。
```
静态工厂模式
```
 private static final Platform PLATFORM = findPlatform();
 
  private static Platform findPlatform() {
    try {
      Class.forName("android.os.Build");
      if (Build.VERSION.SDK_INT != 0) {
        return new Android();
      }
    } catch (ClassNotFoundException ignored) {
    }
    try {
      Class.forName("java.util.Optional");
      return new Java8();
    } catch (ClassNotFoundException ignored) {
    }
    return new Platform();
  }
  可以返回不同的类型的平台
```
关于工厂模式的好处和试用结尾会有彩蛋

### 外观模式（门面模式）
```
Retrofit这个类就相当于外观类,其内部已经封装好了好多的工具类(暂称),比如ServiceMethod,Factory,Converter.Factory等等 也就是当客户端需要使用的时候,直接通过外观类这个Retrofit来调用他们,让他们各司其职。我们只需要和Retrofit这个类进行交互,其他的需要他去调用和把结果返回给我们需要实现的类
```
* 类图

![外观模式类图][2]

### 策略模式
```
CallAdapter就是下面的UML类图的抽象Strategy,那么具体的Strategy就是他的实现类。
```

* 类图
![策略模式类图][3]


### 适配器模式
```
adapt()方法
```
* 类图

![适配器模式类图][4]

### 动态代理模式

```

```
* 类图

![动态代理模式类图][5]

### 参考文档
* [教你用设计模式之建造者模式][6]
* [JAVA设计模式之工厂模式(简单工厂模式+工厂方法模式)][7]
* [深入浅出外观模式][8]
* [设计模式 ( 十八 ) 策略模式Strategy（对象行为型）][9]
* [一个示例让你明白适配器模式][10]
* [动态代理模式][11]

  
  


  [1]: http://upload-images.jianshu.io/upload_images/1506405-6a3b0a14617e1cc8.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/591
  [2]: https://gss3.bdstatic.com/7Po3dSag_xI4khGkpoWK1HF6hhy/baike/c0=baike150,5,5,150,50/sign=d9d0b20e052442a7ba03f5f7b02ac62e/f9198618367adab40d6c93ce81d4b31c8601e4fb.jpg
  [3]: http://my.csdn.net/uploads/201205/11/1336732187_4598.jpg
  [4]: http://img.blog.csdn.net/20140125232924281?watermark/2/text/aHR0cDovL2Jsb2cuY3Nkbi5uZXQvemhhbmdqZ19ibG9n/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70/gravity/SouthEast
  [5]: http://img.blog.csdn.net/20130530111930519
  [6]: http://www.jianshu.com/p/f3cf42416dff
  [7]: http://blog.csdn.net/jason0539/article/details/23020989
  [8]: http://blog.csdn.net/lovelion/article/details/8258121
  [9]: http://blog.csdn.net/hguisu/article/details/7558249/
  [10]: http://blog.csdn.net/zhangjg_blog/article/details/18735243
  [11]: http://blog.csdn.net/lidatgb/article/details/8993131
