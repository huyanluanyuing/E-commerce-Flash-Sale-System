package com.geekq.miaosha.access;

import com.alibaba.fastjson.JSON;
import com.geekq.miaosha.common.enums.ResultStatus;
import com.geekq.miaosha.common.resultbean.ResultGeekQ;
import com.geekq.miaosha.domain.MiaoshaUser;
import com.geekq.miaosha.redis.RedisService;
import com.geekq.miaosha.service.MiaoShaUserService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;

import static com.geekq.miaosha.common.enums.ResultStatus.ACCESS_LIMIT_REACHED;
import static com.geekq.miaosha.common.enums.ResultStatus.SESSION_ERROR;

@Service
public class AccessInterceptor extends HandlerInterceptorAdapter {

    private static Logger logger = LoggerFactory.getLogger(AccessInterceptor.class);

    @Autowired
    MiaoShaUserService userService;

    @Autowired
    RedisService redisService;

    /**
     * 在业务处理器处理请求之前被调用
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // 1. 检查拦截的目标是否为方法（排除静态资源等）
        if (handler instanceof HandlerMethod) {
            logger.info("打印拦截方法handler ：{} ", handler);
            HandlerMethod hm = (HandlerMethod) handler;

            // 2. 获取当前登录用户
            // 这里会从 Cookie 或 URL 参数中拿 Token，并去 Redis 查用户信息
            MiaoshaUser user = getUser(request, response);

            // 3. 将用户信息放入 ThreadLocal 中
            // 这样在后面的 Controller 中，可以直接通过 UserContext.getUser() 获取用户，无需重复查询
            UserContext.setUser(user);

            // 4. 获取方法上的 @AccessLimit 注解
            AccessLimit accessLimit = hm.getMethodAnnotation(AccessLimit.class);
            // 如果方法上没写这个注解，说明不需要限流，直接放行
            if (accessLimit == null) {
                return true;
            }

            // 5. 获取注解中的配置参数
            int seconds = accessLimit.seconds();   // 时间范围
            int maxCount = accessLimit.maxCount(); // 最大访问次数
            boolean needLogin = accessLimit.needLogin(); // 是否需要登录

            String key = request.getRequestURI(); // 以接口路径作为缓存的 Key

            // 6. 登录校验逻辑
            if (needLogin) {
                if (user == null) {
                    // 如果需要登录但用户为空，通过输出流向前端返回“Session过期/未登录”的 JSON
                    render(response, SESSION_ERROR);
                    return false; // 拦截，不执行 Controller
                }
                // 关键：限流 Key 加上用户标识，实现“针对每个用户的限流”
                //最终在 Redis 里生成的 Key 会是 AccessKey:access:[URI]_[Nickname]
                key += "_" + user.getNickname();
            }

            // 7. 接口限流核心逻辑（利用 Redis 实现计数器）
            AccessKey ak = AccessKey.withExpire(seconds);
            Integer count = redisService.get(ak, key, Integer.class);

            if (count == null) {
                // 第一次访问：设值为 1
                redisService.set(ak, key, 1);
            } else if (count < maxCount) {
                // 没超过最大次数：自增
                redisService.incr(ak, key);
            } else {
                // 超过次数限制：返回“访问太频繁”的错误 JSON
                render(response, ACCESS_LIMIT_REACHED);
                return false; // 拦截
            }
        }
        return true; // 所有检查通过，放行
    }

    /**
     * 请求处理完成后（视图渲染后）执行
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        super.afterCompletion(request, response, handler, ex);
        // 8. 关键：移除 ThreadLocal 中的用户信息，防止内存泄漏
        UserContext.removeUser();
    }

    /**
     * 工具方法：当拦截生效时，向前端直接发送 JSON 错误响应
     */
    private void render(HttpServletResponse response, ResultStatus cm) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        OutputStream out = response.getOutputStream();
        String str = JSON.toJSONString(ResultGeekQ.error(cm));
        out.write(str.getBytes("UTF-8"));
        out.flush();
        out.close();
    }

    /**
     * 获取用户信息：兼容 URL 参数和 Cookie 两种方式携带 Token
     */
    private MiaoshaUser getUser(HttpServletRequest request, HttpServletResponse response) {
        String paramToken = request.getParameter(MiaoShaUserService.COOKIE_NAME_TOKEN);
        String cookieToken = getCookieValue(request, MiaoShaUserService.COOKIE_NAME_TOKEN);
        if (StringUtils.isEmpty(cookieToken) && StringUtils.isEmpty(paramToken)) {
            return null;
        }
        String token = StringUtils.isEmpty(paramToken) ? cookieToken : paramToken;
        // 延长 Token 的有效期并返回用户对象
        return userService.getByToken(response, token);
    }

    // 遍历获取指定名称的 Cookie
    private String getCookieValue(HttpServletRequest request, String cookiName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length <= 0) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(cookiName)) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
