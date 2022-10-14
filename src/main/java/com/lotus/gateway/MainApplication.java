package com.lotus.gateway;

import com.lotus.common.utils.Constants;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MainApplication {

    public static void main(String[] args) {
        SpringApplication.run(MainApplication.class, args);

        System.out.println("(♥◠‿◠)ﾉﾞ  " + Constants.APPLY + "启动成功   ლ(´ڡ`ლ)ﾞ  ");
    }

}
