# OkHttp源码解读总结(十二)--->连接池ConectionPool源码总结

标签（空格分隔）： OkHttp源码 学习笔记

---


#### 前言

* 以下的相关知识总结是通过慕课网的相关学习和自己的相关看法,如果有需要的可以去查看一下慕课网的相关教学,感觉还可以。

### 官网介绍
```
Manages reuse of HTTP and HTTP/2 connections for reduced network latency. HTTP requests that share the same 

Address may share a  Connection. This class implements the policy of which connections to keep open for future use.
```

#### get()方法

```
 /**
   * Returns a recycled connection to {@code address}, or null if no such connection exists. The
   * route is null if the address has not yet been routed.
   */
  @Nullable RealConnection get(Address address, StreamAllocation streamAllocation, Route route) {
    assert (Thread.holdsLock(this));
    //遍历connections连接池
    for (RealConnection connection : connections) {
     //根据传入的address和route判断这个链接是否可以使用
      if (connection.isEligible(address, route)) {
        //如果当前链接可以使用 获取连接池
        streamAllocation.acquire(connection, true);
        return connection;
      }
    }
    return null;
  }
```
acquire()方法
```
public void acquire(RealConnection connection, boolean reportedAcquired) {
    assert (Thread.holdsLock(connectionPool));
    if (this.connection != null) throw new IllegalStateException();
    //把从链接池中获取到链接赋值操作
    this.connection = connection;
    this.reportedAcquired = reportedAcquired;
    //把StreamAllocationReference这个弱引用添加到连接池的这个allocations集合中   
    connection.allocations.add(new StreamAllocationReference(this, callStackTrace));
  }
```

#### put()方法
```
  void put(RealConnection connection) {
    assert (Thread.holdsLock(this));
    if (!cleanupRunning) {
      cleanupRunning = true;  
      //执行   cleanupRunnable
      executor.execute(cleanupRunnable);
    }
    //添加
    connections.add(connection);
  }

```

### 总结
* 1、每次网络请求都会产生一个StreamAllocation对象
* 2、每次将StreamAllocation对象的弱引用添加到RealConnection对象的allocations集合中。方便判断集合的大小,然后根据这个大小来判断这个链接是否超过了限制
* 3、从链接吃中获取复用

### Coonection的自动回收
#### GC操作
#### StreamAllocation的数量
#### cleanUpRunnable
```
private final Runnable cleanupRunnable = new Runnable() {
    @Override public void run() {
      while (true) {
        long waitNanos = cleanup(System.nanoTime());
        if (waitNanos == -1) return;
        if (waitNanos > 0) {
          //返回下次需要清理的时间
          long waitMillis = waitNanos / 1000000L;
          waitNanos -= (waitMillis * 1000000L);
          //
          synchronized (ConnectionPool.this) {
            try {
            //等待
              ConnectionPool.this.wait(waitMillis, (int) waitNanos);
            } catch (InterruptedException ignored) {
            }
          }
        }
      }
    }
  };
```
cleanup(long now)方法
```

 long cleanup(long now) {
    int inUseConnectionCount = 0;
    int idleConnectionCount = 0;
    RealConnection longestIdleConnection = null;
    long longestIdleDurationNs = Long.MIN_VALUE;

    // Find either a connection to evict, or the time that the next eviction is due.
    synchronized (this) {
    //主要遍历队列中的RealConnection  标记泄漏的链接  
      for (Iterator<RealConnection> i = connections.iterator(); i.hasNext(); ) {
        RealConnection connection = i.next();

        // If the connection is in use, keep searching.
        //判断当前的链接是否还在使用
        if (pruneAndGetAllocationCount(connection, now) > 0) {
          inUseConnectionCount++;
          continue;
        }

        idleConnectionCount++;

        // If the connection is ready to be evicted, we're done.
        long idleDurationNs = now - connection.idleAtNanos;
        if (idleDurationNs > longestIdleDurationNs) {
          longestIdleDurationNs = idleDurationNs;
          longestIdleConnection = connection;
        }
      }

      if (longestIdleDurationNs >= this.keepAliveDurationNs
          || idleConnectionCount > this.maxIdleConnections) {
        // We've found a connection to evict. Remove it from the list, then close it below (outside
        // of the synchronized block).  
        //移除
        connections.remove(longestIdleConnection);
      } else if (idleConnectionCount > 0) {
        // A connection will be ready to evict soon.
        //5分钟之后再次执行
        return keepAliveDurationNs - longestIdleDurationNs;
      } else if (inUseConnectionCount > 0) {
        // All connections are in use. It'll be at least the keep alive duration 'til we run again.
        return keepAliveDurationNs;
      } else {
        // No connections, idle or in use.
        cleanupRunning = false;
        return -1;
      }
    }
   
    closeQuietly(longestIdleConnection.socket());

    // Cleanup again immediately.
    return 0;
  }
```
pruneAndGetAllocationCount(connection, now)方法
```
 private int pruneAndGetAllocationCount(RealConnection connection, long now) {
    List<Reference<StreamAllocation>> references = connection.allocations;
    for (int i = 0; i < references.size(); ) {
      Reference<StreamAllocation> reference = references.get(i);
      //就是判断当前的链接的弱引用是否为null 
      if (reference.get() != null) {
        i++;
        continue;
      }

      // We've discovered a leaked allocation. This is an application bug.
      StreamAllocation.StreamAllocationReference streamAllocRef =
          (StreamAllocation.StreamAllocationReference) reference;
      String message = "A connection to " + connection.route().address().url()
          + " was leaked. Did you forget to close a response body?";
      Platform.get().logCloseableLeak(message, streamAllocRef.callStackTrace);
     //删除这个引用
      references.remove(i);
      //标志位true
      connection.noNewStreams = true;

      // If this was the last allocation, the connection is eligible for immediate eviction.
      //如果当前的引用集合为空
      if (references.isEmpty()) {
      //
        connection.idleAtNanos = now - keepAliveDurationNs;
        return 0;
      }
    }
    return references.size();
  }
```

### 总结
* 1、okhttp使用了gc回收算法
* 2、StreamAllocation的数量会逐渐变成0
* 3、被线程池检测到并回收,这样就可以保持多个健康的keep-alive连接
