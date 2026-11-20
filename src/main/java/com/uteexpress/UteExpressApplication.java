package com.uteexpress;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class UteExpressApplication {

    public static void main(String[] args) {
        SpringApplication.run(UteExpressApplication.class, args);
    }

    @PostConstruct
    public void initTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }
}
