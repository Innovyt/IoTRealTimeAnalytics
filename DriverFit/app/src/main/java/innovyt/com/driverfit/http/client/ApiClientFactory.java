package innovyt.com.driverfit.http.client;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;


/**
 * It is responsible to create ApiClient with GrantType.REFRESH_TOKEN.
 */
public class ApiClientFactory {
    private static ApiClient client = null;
    private static ApiClient clientAuth = null;
    private static Object object = new Object();
    private static ApiClientFactory factory = new ApiClientFactory();
    private static String serverIp = "localhost";

    private ApiClientFactory() {
            }

    public static ApiClient create() {
        synchronized (object) {
            if (client == null) {
                //Fetch Server IP address from SharedPreferences. This value is set by settings page
                client = new ApiClient();
                client.getAdapterBuilder().baseUrl("http://" + "api.metro.net" + ":80/");
                //client.getOkBuilder().interceptors().add(new LoggingInterceptor());
                client.addAuthsToOkBuilder(client.getOkBuilder());
            }
        }
        return client;
    }


    private static class LoggingInterceptor implements Interceptor {
        Logger logger = LoggerFactory.getLogger(LoggingInterceptor.class);

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();

            long t1 = System.nanoTime();
            this.logger.debug(String.format("Sending request %s on %s%n%s",
                    request.url(), chain.connection(), request.headers()));

            Response response = chain.proceed(request);

            long t2 = System.nanoTime();
            logger.debug(String.format("Received response for %s in %.1fms%n%s",
                    response.request().url(), (t2 - t1) / 1e6d, response.headers()));

            return response;
        }
    }
}
