package com.lotus.gateway.filter;

import com.alibaba.fastjson.JSON;
import com.lotus.auth.entity.LoginUser;
import com.lotus.auth.service.TokenService;
import com.lotus.auth.utils.AuthConstants;
import com.lotus.auth.utils.JwtUtils;
import com.lotus.auth.utils.TokenUtils;
import com.lotus.common.R;
import com.lotus.common.utils.IPUtils;
import com.lotus.common.utils.ServletUtils;
import com.lotus.common.utils.StringUtils;
import com.lotus.gateway.config.properties.IgnoreWhiteProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关鉴权
 *
 * @author changjiz
 */
@Component
public class AuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    // 排除过滤的 uri 地址，nacos自行添加
    @Autowired
    private IgnoreWhiteProperties ignoreWhite;
    @Autowired
    private TokenService tokenService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("lotus----请求路径:{}", exchange.getRequest().getPath());

        ServerHttpRequest request = exchange.getRequest();
        ServerHttpRequest.Builder mutate = request.mutate();

        String url = request.getURI().getPath();
        // 跳过不需要验证的路径
        if (StringUtils.matches(url, ignoreWhite.getWhites())) {
            return chain.filter(exchange);
        }
        String token = TokenUtils.getToken(request);
        if (StringUtils.isEmpty(token)) {
            return unauthorizedResponse(exchange, "令牌不能为空");
        }
        String sessionId;
        try {
            sessionId = JwtUtils.getSessionId(token);
        } catch (Exception e) {
            return unauthorizedResponse(exchange, "令牌有误！");
        }

        if (StringUtils.isEmpty(sessionId)) {
            return unauthorizedResponse(exchange, "令牌已过期或验证不正确！");
        }
        LoginUser cacheLoginUser = tokenService.getLoginUser(token);
        if (null == cacheLoginUser) {
            return unauthorizedResponse(exchange, "登录状态已过期");
        }
        if (!sessionId.equals(cacheLoginUser.getSessionId())) {
            return unauthorizedResponse(exchange, "用户已退出");
        }
        if (cacheLoginUser.getIpAddr().equals(IPUtils.getIpAddr(ServletUtils.getRequest()))) {
            return unauthorizedResponse(exchange, "IP地址发生变化");
        }

        // 刷新session
        tokenService.touch(cacheLoginUser);

        // 设置用户信息到请求
        addHeader(mutate, AuthConstants.SESSION_ID, cacheLoginUser.getSessionId());
        addHeader(mutate, AuthConstants.USER_ID, cacheLoginUser.getId());
        addHeader(mutate, AuthConstants.ROLE_ID, cacheLoginUser.getRoleId());
        addHeader(mutate, AuthConstants.TENANT_ID, cacheLoginUser.getTenantId());
        addHeader(mutate, AuthConstants.DEVICE_TYPE, cacheLoginUser.getDeviceType());
        // 内部请求来源参数清除
        removeHeader(mutate, AuthConstants.FROM_SOURCE);
        return chain.filter(exchange.mutate().request(mutate.build()).build());
    }

    private void addHeader(ServerHttpRequest.Builder mutate, String name, Object value) {
        if (value == null) {
            return;
        }
        String valueStr = value.toString();
        String valueEncode = ServletUtils.urlEncode(valueStr);
        mutate.header(name, valueEncode);
    }

    private void removeHeader(ServerHttpRequest.Builder mutate, String name) {
        mutate.headers(httpHeaders -> httpHeaders.remove(name)).build();
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String msg) {
        log.error("[鉴权异常处理]请求路径:{}", exchange.getRequest().getPath());

        // 权限不够拦截
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(JSON.toJSONString(R.error(HttpStatus.UNAUTHORIZED.value(), msg)).getBytes(StandardCharsets.UTF_8));
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.OK);
        //指定编码，否则在浏览器中会中文乱码
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        return response.writeWith(Mono.just(buffer));

    }

    @Override
    public int getOrder() {
        return -200;
    }
}