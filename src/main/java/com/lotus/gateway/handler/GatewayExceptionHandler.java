package com.lotus.gateway.handler;

import com.alibaba.fastjson.JSON;
import com.lotus.common.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关统一异常处理
 *
 * @author changjiz
 */
@Order(-1)
@Configuration
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus httpStatus;
        String msg;

        if (ex instanceof NotFoundException) {
            msg = "服务未找到";
            httpStatus = HttpStatus.NOT_FOUND;
        } else if (ex instanceof ResponseStatusException) {
            ResponseStatusException responseStatusException = (ResponseStatusException) ex;
            httpStatus = responseStatusException.getStatus();
            msg = responseStatusException.getMessage();
        } else {
            msg = "内部服务器错误";
            httpStatus = HttpStatus.BAD_GATEWAY;
        }

        log.error("[网关异常处理]请求路径:{},异常信息:{}", exchange.getRequest().getPath(), ex.getMessage());

        response.setStatusCode(httpStatus);
        // 输出错误信息
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(JSON.toJSONString(R.error(httpStatus.value(), msg)).getBytes(StandardCharsets.UTF_8));
        //指定编码，否则在浏览器中会中文乱码
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        return response.writeWith(Mono.just(buffer));
    }
}