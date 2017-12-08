package ruolan.com.opensourcesummary;

import android.util.Log;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by wuyinlei on 2017/12/7.
 *
 * @function
 */

public class TestRetrofit {

    public static Retrofit getRetrofit() {

        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(10, TimeUnit.SECONDS);


        return new Retrofit.Builder()
                .client(builder.build())
                .baseUrl("http://m2.qiushibaike.com/")  //设置请求网络地址的url(基地址  这个地址一定要"/"结尾  要不会出现问题)
                .addConverterFactory(GsonConverterFactory.create()) //设置数据解析器  默认使用的Gson
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create()) //设置支持RxJava转换器
                .build();

    }

    public static void getData(){

        TestInterface service = getRetrofit().create(TestInterface.class);

        Call<HttpResult<List<TestBean>>> qiuShiJson = service.getQiuShiJsonString();

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

    }

}
