package com.fion.error.code._01_concurrent_util;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 *
 */
@Slf4j
@RestController
@RequestMapping("concurrent/util/hashmap")
public class ConcurrentHashMapController {

    /**
     * 线程数量
     */
    private static int THREAD_COUNT = 10;

    /**
     * 总元素数量
     */
    private static int ITEM_COUNT_1000 = 1000;
    private static int ITEM_COUNT_10 = 10;

    /**
     * 循环次数
     */
    private static int LOOP_COUNT = 10000000;



    /**
     * 访问：http://localhost:8080/concurrent/util/hashmap/wrong
     *
     * 使用了 ConcurrentHashMap，不代表对它的多个操作之间的状态是一致的，是没有其他线程在操作它的，如果需要确保需要手动加锁。
     *
     * 诸如 size、isEmpty 和 containsValue 等聚合方法，在并发情况下可能会反映 ConcurrentHashMap 的中间状态。
     * 因此在并发情况下，这些方法的返回值只能用作参考，而不能用于流程控制。显然，利用 size 方法计算差异值，是一个流程控制。
     *
     * 诸如 putAll 这样的聚合方法也不能确保原子性，在 putAll 的过程中去获取数据可能会获取到部分数据。
     *
     * @return
     * @throws InterruptedException
     */
    @GetMapping("wrong")
    public String wrong() throws InterruptedException {
        ConcurrentHashMap<String, Long> concurrentHashMap = getData(ITEM_COUNT_1000 - 100);
        log.info("init size: {}", concurrentHashMap.size());

        ForkJoinPool forkJoinPool = new ForkJoinPool(THREAD_COUNT);
        // 使用线程池并发处理逻辑
        forkJoinPool.execute(() -> IntStream.rangeClosed(1, 10).parallel().forEach(v -> {
            // 查询还需要补充多少个元素
            int gap = ITEM_COUNT_1000 - concurrentHashMap.size();
            log.info("gap size: {}", gap);
            // 补充元素
            concurrentHashMap.putAll(getData(gap));
        }));
        // 等待所有任务完成
        forkJoinPool.shutdown();
        forkJoinPool.awaitTermination(1, TimeUnit.HOURS);
        log.info("finish size: {}", concurrentHashMap.size());
        return "OK";
    }

    /**
     * 访问：http://localhost:8080/concurrent/util/hashmap/right
     *
     * 将查看操作和补充数据操作加锁，保证原子性
     *
     * @return
     * @throws InterruptedException
     */
    @GetMapping("right")
    public String right() throws InterruptedException {
        ConcurrentHashMap<String, Long> concurrentHashMap = getData(ITEM_COUNT_1000 - 100);
        log.info("init size: {}", concurrentHashMap.size());
        ForkJoinPool forkJoinPool = new ForkJoinPool(THREAD_COUNT);
        forkJoinPool.execute(() -> IntStream.rangeClosed(1, 10).parallel().forEach(v -> {
            synchronized (concurrentHashMap) {
                int gap = ITEM_COUNT_1000 - concurrentHashMap.size();
                log.info("gap size: {}", gap);
                concurrentHashMap.putAll(getData(gap));
            }
        }));
        forkJoinPool.shutdown();
        forkJoinPool.awaitTermination(1, TimeUnit.HOURS);
        log.info("finish size: {}", concurrentHashMap.size());
        return "OK";
    }

    /**
     * 帮助方法，用来获得一个指定元素数量模拟数据的ConcurrentHashMap
     *
     * @param count
     * @return
     */
    private ConcurrentHashMap<String, Long> getData(int count) {
        return LongStream.rangeClosed(1, count)
                .boxed()
                .collect(Collectors.toConcurrentMap(i -> UUID.randomUUID().toString(),
                        Function.identity(),
                        (o1, o2) -> o1,
                        ConcurrentHashMap::new));
    }

    /**
     * 这段代码在功能上没有问题，但无法充分发挥 ConcurrentHashMap 的威力
     *
     * @return
     * @throws InterruptedException
     */
    private Map<String, Long> normaluse() throws InterruptedException {
        ConcurrentHashMap<String, Long> freqs = new ConcurrentHashMap<>(ITEM_COUNT_10);
        ForkJoinPool forkJoinPool = new ForkJoinPool(THREAD_COUNT);
        forkJoinPool.execute(() -> IntStream.rangeClosed(1, LOOP_COUNT).parallel().forEach(i -> {
            //获得一个随机的Key
            String key = "item" + ThreadLocalRandom.current().nextInt(ITEM_COUNT_10);
            synchronized (freqs) {
                if (freqs.containsKey(key)) {
                    //Key存在则+1
                    freqs.put(key, freqs.get(key) + 1);
                } else {
                    //Key不存在则初始化为1
                    freqs.put(key, 1L);
                }
            }
        }));
        forkJoinPool.shutdown();
        forkJoinPool.awaitTermination(1, TimeUnit.HOURS);
        return freqs;
    }

    /**
     * 使用 ConcurrentHashMap 的原子性方法 computeIfAbsent 来做复合逻辑操作，
     * 判断 Key 是否存在 Value，如果不存在则把 Lambda 表达式运行后的结果放入 Map 作为 Value，
     * 也就是新创建一个 LongAdder 对象，最后返回 Value。
     *
     * 由于 computeIfAbsent 方法返回的 Value 是 LongAdder，是一个线程安全的累加器，
     * 因此可以直接调用其 increment 方法进行累加。
     *
     * @return
     * @throws InterruptedException
     */
    private Map<String, Long> gooduse() throws InterruptedException {
        ConcurrentHashMap<String, LongAdder> freqs = new ConcurrentHashMap<>(ITEM_COUNT_10);
        ForkJoinPool forkJoinPool = new ForkJoinPool(THREAD_COUNT);
        forkJoinPool.execute(() -> IntStream.rangeClosed(1, LOOP_COUNT).parallel().forEach(i -> {
            //获得一个随机的Key
            String key = "item" + ThreadLocalRandom.current().nextInt(ITEM_COUNT_10);
            // //利用computeIfAbsent()方法来实例化LongAdder，然后利用LongAdder来进行线程安全计数
            freqs.computeIfAbsent(key, k -> new LongAdder()).increment();
        }));
        forkJoinPool.shutdown();
        forkJoinPool.awaitTermination(1, TimeUnit.HOURS);

        // 因为我们的Value是LongAdder而不是Long，所以需要做一次转换才能返回
        return freqs.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, v -> v.getValue().longValue()));
    }

    @Test
    public void test() throws InterruptedException {
        long normalStart = System.currentTimeMillis();
        Map<String, Long> normaluse = normaluse();
        long normalCost = System.currentTimeMillis() - normalStart;
        long normalSum = normaluse.values().stream().reduce(0L, Long::sum);
        log.info("normal use --> cost: {}, sum: {}", normalCost, normalSum);

        long goodStart = System.currentTimeMillis();
        Map<String, Long> gooduse = gooduse();
        long goodCost = System.currentTimeMillis() - goodStart;
        long goodSum = gooduse.values().stream().reduce(0L, Long::sum);
        log.info("good use --> cost: {}, sum: {}", goodCost, goodSum);
    }
}
