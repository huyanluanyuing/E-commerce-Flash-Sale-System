package com.geekq.miaosha.access;

import com.geekq.miaosha.redis.BasePrefix;
//用于生成在 Redis 中存储限流计数的 Key
public class AccessKey extends BasePrefix {

    private AccessKey(int expireSeconds, String prefix) {
        super(expireSeconds, prefix);
    }

    //这是一个静态工厂方法。它会创建一个带有指定过期时间的 Key 前缀
    //最终在 Redis 里生成的 Key 会是 Access:access:[URI]_[Nickname]
    public static AccessKey withExpire(int expireSeconds) {
        return new AccessKey(expireSeconds, "access");
    }

}
