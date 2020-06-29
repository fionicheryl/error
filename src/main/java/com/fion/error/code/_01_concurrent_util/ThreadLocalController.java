package com.fion.error.code._01_concurrent_util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 使用了并发工具类库，线程安全就高枕无忧了吗？
 * 参考：https://time.geekbang.org/column/article/209494
 */
@Slf4j
@RestController
@RequestMapping("concurrent/util/thread")
public class ThreadLocalController {

    /**
     * 使用 ThreadLocal 存放一个 Integer 的值，来暂且代表需要在线程中保存的用户信息
     * 这个值初始是 null
     */
    private static final ThreadLocal<Integer> currentUser = ThreadLocal.withInitial(() -> null);

    /**
     * 访问：http://localhost:8080/concurrent/util/thread/wrong?userId=4
     *
     * 按理说，在设置用户信息之前第一次获取的值始终应该是 null，但我们要意识到，程序运行在 Tomcat 中，
     * 执行程序的线程是 Tomcat 的工作线程，而 Tomcat 的工作线程是基于线程池的。
     *
     * 顾名思义，线程池会重用固定的几个线程，一旦线程重用，那么很可能首次从 ThreadLocal 获取的值是之前其他用户的请求遗留的值。
     * 这时，ThreadLocal 中的用户信息就是其他用户的信息。
     *
     * @param userId
     * @return
     */
    @GetMapping("wrong")
    public Map wrong(@RequestParam("userId") Integer userId) {
        // 设置用户信息之前先查询一次ThreadLocal中的用户信息
        String before = Thread.currentThread().getName() + ":" + currentUser.get();
        // 设置用户信息到ThreadLocal
        currentUser.set(userId);
        // 设置用户信息之后再查询一次ThreadLocal中的用户信息
        String after = Thread.currentThread().getName() + ":" + currentUser.get();
        // 汇总输出两次查询结果
        Map<String, String> result = new HashMap<>();
        result.put("before", before);
        result.put("after", after);
        log.info("======================start=======================");
        log.info("before = {}", before);
        log.info("after = {}", after);
        log.info("=======================end========================");
        return result;
    }

    /**
     * 访问：http://localhost:8080/concurrent/util/thread/right?userId=3
     *
     * 使用类似 ThreadLocal 工具来存放一些数据时，需要特别注意在代码运行完后，显式地去清空设置的数据。
     * 如果在代码中使用了自定义的线程池，也同样会遇到这个问题。
     *
     * @param userId
     * @return
     */
    @GetMapping("right")
    public Map right(@RequestParam("userId") Integer userId) {
        String before = Thread.currentThread().getName() + ":" + currentUser.get();
        currentUser.set(userId);
        try {
            String after = Thread.currentThread().getName() + ":" + currentUser.get();
            Map<String, String> result = new HashMap<>();
            result.put("before", before);
            result.put("after", after);
            log.info("======================start=======================");
            log.info("before = {}", before);
            log.info("after = {}", after);
            log.info("=======================end========================");
            return result;
        } finally {
            currentUser.remove();
        }

    }

}
