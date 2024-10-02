package cancel;

import java.time.Duration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

/**
 * @author fdse
 */
@SpringBootApplication
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableAsync
@IntegrationComponentScan
@EnableSwagger2
@EnableDiscoveryClient
public class CancelApplication {

    public static void main(String[] args) {
        SpringApplication.run(CancelApplication.class, args);
    }

    @LoadBalanced
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return new RestTemplateBuilder()
            // .requestFactory(this::requestFactory)
            .setReadTimeout(Duration.ofMillis(200))
            .build();
    }

    //     private HttpComponentsClientHttpRequestFactory requestFactory() {

    //     RequestConfig requestConfig = RequestConfig.custom()
    //         .setConnectionRequestTimeout(2000)
    //         .setSocketTimeout(2000)
    //         .build();
    //     PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
    //     connectionManager.setMaxTotal(5);
    //     connectionManager.setDefaultMaxPerRoute(5);
    //     CloseableHttpClient httpClient = HttpClientBuilder.create()
    //                                                       .setConnectionManager(connectionManager)
    //                                                       .setDefaultRequestConfig(requestConfig)
    //                                                       .build();
    //     return new HttpComponentsClientHttpRequestFactory(httpClient);
    // }
}
