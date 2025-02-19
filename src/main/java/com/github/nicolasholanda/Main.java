package com.github.nicolasholanda;

import java.time.Duration;
import java.util.Date;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.nicolasholanda.model.Student;

public class Main {

    private static Cache<String, Student> cache = buildCache();

    public static void main(String[] args) {
        Student student;

        // Initially the cache is empty
        student = cache.getIfPresent("1");
        System.out.println("Student 1 when cache is empty: " + student);

        // Explicitly populating cache
        cache.put("1", new Student("John Doe", new Date()));
        student = cache.getIfPresent("1");
        System.out.println("Student 1 after cache is populated: " + student);

        // Caffeine provides a simpler way to populate cache when object is not present
        // in a single line
        student = cache.get("2", key -> new Student("Foo Bar", new Date()));
        System.out.println("Student 2 after cache is populated: " + student);
    }

    // Builds a cache object using Caffeine's builder.
    private static Cache<String, Student> buildCache() {
        return Caffeine.newBuilder()
                .maximumSize(10)
                .expireAfterWrite(Duration.ofSeconds(20))
                .build();
    }
}