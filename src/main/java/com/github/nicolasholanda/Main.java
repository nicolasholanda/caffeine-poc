package com.github.nicolasholanda;

import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.nicolasholanda.model.Student;
import com.github.nicolasholanda.service.StudentService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class Main {

    // Using a service to mimic a real student service
    private static StudentService studentService = new StudentService();

    public static void main(String[] args) {
        log.info("Starting simpleCacheDemo...");
        simpleCacheDemo();
        log.info("Finishing simpleCacheDemo...");

        log.info("Starting synchronousLoadingCacheDemo...");
        synchronousLoadingCacheDemo();
        log.info("Finishing synchronousLoadingCacheDemo...");

        log.info("Starting asyncLoadingCacheDemo...");
        asyncLoadingCacheDemo();
        log.info("Finishing asyncLoadingCacheDemo...");

        log.info("Starting sizeBasedEvictionDemo...");
        sizeBasedEvictionDemo();
        log.info("Finishing sizeBasedEvictionDemo...");

        log.info("Starting weightBasedEvictionDemo...");
        weightBasedEvictionDemo();
        log.info("Finishing weightBasedEvictionDemo...");
        
        log.info("Starting timeBasedEvictionDemo...");
        timeBasedEvictionDemo();
        log.info("Finishing timeBasedEvictionDemo...");

        log.info("Starting referenceBasedEvictionDemo...");
        referenceBasedEvictionDemo();
        log.info("Finishing referenceBasedEvictionDemo...");

        log.info("Starting refreshAfterWriteDemo...");
        refreshAfterWriteDemo();
        log.info("Finished refreshAfterWriteDemo...");
    }

    private static void simpleCacheDemo() {
        Student student;

        // Builds a cache object using Caffeine's builder.
        Cache<Integer, Student> simpleCache = Caffeine.newBuilder()
                .maximumSize(10)
                .expireAfterWrite(Duration.ofSeconds(20))
                .build();

        // Initially the cache is empty
        student = simpleCache.getIfPresent(1);
        log.info("Student 1 when cache is empty: {}", student);

        // Explicitly populating cache
        simpleCache.put(1, new Student(1, "John Doe", new Date()));
        student = simpleCache.getIfPresent(1);
        log.info("Student 1 after cache is populated: {}", student);

        // Get method can takes a fallback function to provide values when the value is
        // not present
        student = simpleCache.get(2, key -> new Student(2, "Foo Bar", new Date()));
        log.info("Student 2 after defining a fallback value for get method: {}", student);
    }

    private static void synchronousLoadingCacheDemo() {
        /*
         * A Synchronous Loading cache takes a function to be used for initializing
         * values.
         * In this case, when a student is not found in cache, Caffeine will load it
         * from StudentService.
         * That approach is similar to providing a fallback function to the cache.get
         * method.
         */
        LoadingCache<Integer, Student> loadingCache = Caffeine.newBuilder()
                .maximumSize(10)
                .expireAfterWrite(Duration.ofSeconds(20))
                .build(id -> studentService.findById(id));

        /*
         * Let's retrive a student from cache. In the first time, Caffeine will take a
         * long time
         * because it will load from studentService.
         */
        Student student;
        int studentId = 1;

        long initialTime = System.currentTimeMillis();
        student = loadingCache.get(studentId);
        long finalTime = System.currentTimeMillis();

        log.info("Student {} took {} ms to be retrieved from cache: {}", studentId, (finalTime - initialTime), student);

        // Second retrieval must take less time
        initialTime = System.currentTimeMillis();
        student = loadingCache.get(studentId);
        finalTime = System.currentTimeMillis();

        log.info("Student {} took {} ms to be retrieved from cache: {}", studentId, (finalTime - initialTime), student);

        // It's also possible to search multiple students at once.
        Map<Integer, Student> foundStudents = loadingCache.getAll(Arrays.asList(studentId, studentId + 1));
        log.info("Found students for getAll method: {}", foundStudents);
    }

    private static void asyncLoadingCacheDemo() {
        /*
         * Async loading cache works similarly to the loading cache, the difference is
         * that
         * it returns a CompletableFuture holding the value.
         */
        AsyncLoadingCache<Integer, Student> asyncLoadingCache = Caffeine.newBuilder()
                .maximumSize(10)
                .expireAfterWrite(Duration.ofSeconds(20))
                .buildAsync(id -> studentService.findById(id));

        /*
         * Let's retrive a student from cache. The resulting object will be contained in
         * a future
         */
        CompletableFuture<Student> studentFuture;

        int studentId = 1;
        studentFuture = asyncLoadingCache.get(studentId);

        Student student = null;
        try {
            student = studentFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        log.info("Student was asynchronously retrieved. Result: {}", student);
    }

    private static void sizeBasedEvictionDemo() {

        /*
         * By specifying the maximumSize property, Caffeine is able to evict cache
         * records when max is reached.
         */
        LoadingCache<Integer, Student> cache = Caffeine.newBuilder()
                .maximumSize(2)
                .build(key -> studentService.findById(key));

        // Initial size should be 0
        log.info("Initial cache size: {}", cache.estimatedSize());

        // Since this is a LoadingCache, the get method will produce a student in cache
        cache.get(1);

        // Since eviction is an asynchronous process, the size of a cache can only be
        // estimated
        log.info("Cache size after getting first student: {}", cache.estimatedSize());

        /*
         * This will produce another student in cache. Since 2 is the maximumSize of
         * this cache, all entries
         * will be now eligible for eviction.
         */
        cache.get(2);

        // Eviction is an asynchronous process, so cleanUp method forces the app to wait
        // for eviction to complete.
        cache.cleanUp();

        log.info("Cache size after size-based eviction: {}", cache.estimatedSize());
    }

    private static void weightBasedEvictionDemo() {
        /*
         * Sometimes, entries might have different costs in cache storage. For that, we
         * may introduce a weigher function to calculate weight of an entry and specify
         * maximumWeight to enable weight-based eviction.
         * When adding an entry, Caffeine checks if it will exceed the maximumWeight.
         * If it does, then entries will be evicted to make room for the new entry.
         * Entries less frequently used (LFU) are prioritized for eviction.
         * If the new entry itself has cost greater than maximumCost, it will not be
         * added to cache at all.
         */
        LoadingCache<Integer, Student> cache = Caffeine.newBuilder()
                .maximumWeight(20)
                .weigher((Integer id, Student student) -> id * 5) // Weight of a student will be its id multiplied by 5
                .removalListener((id, student, cause) -> log.info("Evicted student with id {} due to {}", id, cause))
                .build(id -> studentService.findById(id));

        // Initial size should be 0
        log.info("Initial cache size: {}", cache.estimatedSize());

        // Add an entry with cost 5.
        cache.get(1);
        log.info("Cache size after getting first student: {}", cache.estimatedSize());

        /*
         * Now cache weight will increase by 20, reaching a total of 25, thus
         * first entry should be evicted to make room for this new entry.
         */
        cache.get(4);
        cache.cleanUp();

        log.info("Cache size after weight-based eviction: {}", cache.estimatedSize());

        // Cleaning all entries for the next test
        cache.invalidateAll();
        cache.cleanUp();

        log.info("Cache was cleaned up. New size: {}", cache.estimatedSize());

        // An entry with cost 25 (greater than maximumSize) should not be added at all.
        cache.get(5);
        cache.cleanUp();

        log.info("Cache size after trying to add an entry with cost greater than max: {}", cache.estimatedSize());
    }

    private static void timeBasedEvictionDemo() {
        /*
         * Time-based eviction can be specified to happen based on:
         * 1 - Last read - entry expires after period since its last read
         * 2 - Last write - entry expires after period since its last write
         * 3 - Custom - entry expires after a period since a custom calculated date
         */
        LoadingCache<Integer, Student> cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(1)) // Eviction based on write
                .build(id -> studentService.findById(id));

        // Add student 1 to cache
        cache.get(1);
        log.info("Cache size after getting one student: {}", cache.estimatedSize());

        try {
            Thread.sleep(1000); // Waits for 1 second
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        cache.cleanUp();

        log.info("Cache size after 1 second: {}", cache.estimatedSize());
    }

    public static void referenceBasedEvictionDemo() {
        LoadingCache<Integer, Student> cache = Caffeine.newBuilder()
            .weakKeys()      // Evicts entries when keys are no longer referenced
            .softValues()    // Evicts values under memory pressure
            .removalListener((id, value, cause) -> log.info("Evicted student with id {} due to {}", id, cause))
            .build(id -> studentService.findById(id));

        // Create a key with a strong reference
        Integer strongKey = 1;
        cache.get(strongKey);

        // Create a weakly referenced key
        Integer weakKey = 2;
        cache.get(weakKey);

        log.info("Before GC: {}", cache.estimatedSize());

        // Remove strong reference to weakKey, making it eligible for GC
        weakKey = null;

        // Suggest the JVM to run garbage collection
        System.gc();

        try {
            Thread.sleep(1000);  // Wait a bit for GC to complete
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        log.info("After GC (it might still the same): {}", cache.estimatedSize());

        // strongKey should still be in the cache, but weakKey should be evicted
        log.info("Contains strongKey? " + (cache.getIfPresent(strongKey) != null));
    }

    private static void refreshAfterWriteDemo() {
        LoadingCache<Integer, Student> cache = Caffeine.newBuilder()
                .refreshAfterWrite(Duration.ofSeconds(5)) // Auto-refresh every 5 seconds
                .build(id -> studentService.findById(id));
    
        log.info("Initial student: {}", cache.get(1));
    
        try {
            Thread.sleep(6000); // Wait for 6 seconds to allow refresh
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    
        log.info("Student after refresh: {}", cache.get(1)); // Should trigger refresh
    }    
}