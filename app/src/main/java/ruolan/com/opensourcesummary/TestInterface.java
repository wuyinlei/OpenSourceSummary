package ruolan.com.opensourcesummary;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;

/**
 * Created by wuyinlei on 2017/12/7.
 *
 * @function 测试接口
 */

public interface TestInterface {


    //每个方法参数都需要进行注解标志 要不然就会报错  这个在后面的分析详细逻辑的时候会讲
    @GET("article/list/latest?page=1")
    Call<HttpResult<List<TestBean>>> getQiuShiJsonString();


}
