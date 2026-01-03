package com.geekq.miaosha.access;

import com.geekq.miaosha.domain.MiaoshaUser;

public class UserContext {
    //ThreadLocal 会为每个线程创建一个独立的变量副本
    private static ThreadLocal<MiaoshaUser> userHolder = new ThreadLocal<MiaoshaUser>();

    public static MiaoshaUser getUser() {
        return userHolder.get();
    }

    public static void setUser(MiaoshaUser user) {
        userHolder.set(user);
    }

    public static void removeUser() {
        userHolder.remove();
    }

}
