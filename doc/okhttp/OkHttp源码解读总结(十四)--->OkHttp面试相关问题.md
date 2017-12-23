# OkHttp源码解读总结(十四)--->OkHttp面试相关问题

标签（空格分隔）： OkHttp源码 学习笔记

---


#### 前言

* 以下的相关知识总结是通过慕课网的相关学习和自己的相关看法,如果有需要的可以去查看一下慕课网的相关教学,感觉还可以。

## 目录
* 1、Android基础网络编程:socket、HttpClient和HttpURLConnection
* 2、了解WebSocket吗?知道和socket的区别么?okhttp是如何处理websocket的相关问题的?
* 3、Http如何处理缓存?OkHttp是如何处理缓存相关问题的?
* 4、断点续传的原理?如何实现?okhttp中如何实现相关问题？
* 5、多线程下载原理,okhttp如何实现?
* 6、文件上传如何做?原理,okhttp如何实现文件上传?
* 7、json数据如何解析?okhttp中如何解析json类型的数据?
* 8、okhttp如何处理https?


### 4、断点续传的原理?如何实现?okhttp中如何实现相关问题？

* 断点续传:从文件已经下载完的地方开始继续下载。客户端需要指定从哪个地方开始下载。
    * Range 头部信息-->设置开始位置
    
```
OkHttpClient client = new OkHttpClient;
Request request = new Request.Builder()
    .addHeader("RANGE","bytes=" + downloadLength+"-") //断点续传需要用到的  指示下载的区间
    .url(downloadUrl)
    .build();
    
    try{
        Response response = client.newCall(request).execute();
        if(response != null){
            is = response.body().byteStream();
            savedFile = new RandomAccessFile(file,"rw");
            savedFile.seek(downloadLength); //跳过已经下载过的字节
            
            //写入操作
        }
    } catch(Execption e){
    
    }

```
```
HttpURLConnection用的则是httpURLConnection.setRequestProperty("RANGE","bytes=20000");
RandomAccessFile savedFile = new RandomAccessFile(file,"rw");
savedFile.seek(20000);
//....

```

### 5、多线程下载原理,okhttp如何实现?
* 多线程下载:每个线程只负责下载文件的一部分

普通的下载
```
public void downLoad() throws Exception {

        URL url = new URL(path);

        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestMethod("GET");
        urlConnection.setConnectTimeout(10000);

        int code = urlConnection.getResponseCode();
        if (code == 200) {
            //获取资源的大小
            int connectionLength = urlConnection.getContentLength();
            //在本地创建一个与资源同样大小的文件来占位
            RandomAccessFile randomAccessFile = new RandomAccessFile(new File("cache", getFileName(url)), "hello");
            randomAccessFile.setLength(connectionLength);

            int blockSize = connectionLength / threadCount;
            for (int threadId = 0; threadId < threadCount; threadId++) {
                int startIndex = threadId * blockSize;
                int endIndex = (threadId + 1) * blockSize - 1;
                if (threadId == (threadCount - 1)) {
                    endIndex = connectionLength - 1;
                }

                new DownloadThread(threadId,startIndex,endIndex).start();  //开启线程下载

            }

            randomAccessFile.close();

        }

    }

    private String getFileName(URL url) {


        return "";
    }


    private class DownloadThread extends Thread {

        private int mThreadId;
        private int mStartIndex;
        private int mEndIndex;

        public DownloadThread(int threadId, int startIndex, int endIndex) {
            this.mThreadId = threadId;
            this.mStartIndex = startIndex;
            this.mEndIndex = endIndex;
        }

        @Override
        public void run() {
            System.out.print("线程" + mThreadId +  " 开始下载");

            try{
                URL url = new URL(path);

                File downThreadFile = new File(targetFilePath,"downThread_" + mThreadId + ".dt");
                RandomAccessFile randomAccessFile = null;
                if (downThreadFile.exists()){
                    //如果文件存在
                    randomAccessFile = new RandomAccessFile(downThreadFile,"rwd");
                    String startIndex_str = randomAccessFile.readLine();
                    if (null != startIndex_str || !"".equals(startIndex_str)){
                        this.mStartIndex = Integer.parseInt(startIndex_str) - 1;//设置下载起点
                    }
                } else {
                    randomAccessFile = new RandomAccessFile(downThreadFile,"rwd");
                }

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);

                connection.setRequestProperty("Range","bytes=" + mStartIndex + "-" + mEndIndex );

                if (connection.getResponseCode() == 206){
                    InputStream inputStream = connection.getInputStream();
                    RandomAccessFile accessFile = new RandomAccessFile(new File(targetFilePath,getFileName(url)),"rw");
                    accessFile.seek(mStartIndex);

                    byte[] buffer = new byte[1024];
                    int length = -1;
                    int total = 0;// 记录本次下载文件的大小
                    while ((length = inputStream.read(buffer)) > 0){
                        accessFile.write(buffer,0,length);
                        total += length;
                        randomAccessFile.seek(0);
                        randomAccessFile.write((mStartIndex + total + "").getBytes("UTF-8"));
                    }

                    randomAccessFile.close();
                    inputStream.close();
                    accessFile.close();
                    cleanTemp(downThreadFile);  //清除临时文件 

                }
            }catch(Exception e) {

            }
        }
    }

```
### 6、文件上传如何做?原理,okhttp如何实现文件上传?
* Http的contentTyep
    * 指定请求和响应的HTTP的内容类型
```
 public void updateFile(String actionUrl, HashMap<String ,String> parmersMap){
        try {
            String requestUrl = String.format("%s%s","upload",actionUrl);
            MultipartBody.Builder builder = new MultipartBody.Builder();
            builder.setType(MultipartBody.FORM);
            for (String key : parmersMap.keySet()) {
                Object object = parmersMap.get(key);
                if (!(object instanceof File)){
                    builder.addFormDataPart(key,object.toString());
                } else {
                    File file = (File) object;
                    builder.addFormDataPart(key,file.getName(), RequestBody.create(MediaType.parse(contentType),file));
                }
            }

            RequestBody body = builder.build();
            final Request request = new Request.Builder().url(requestUrl)
                    .post(body).build();

            final Call call = getOkHttpClient().newCall(request);
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {

                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {

                }
            });

        } catch (Exception e){

        }
    }
```
    
