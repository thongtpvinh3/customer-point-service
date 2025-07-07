package thong.test.customerpointservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class CustomerPointServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerPointServiceApplication.class, args);
    }

}
