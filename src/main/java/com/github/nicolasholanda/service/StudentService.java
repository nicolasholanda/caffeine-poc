package com.github.nicolasholanda.service;

import java.util.Date;

import com.github.nicolasholanda.model.Student;

import lombok.extern.log4j.Log4j2;

// Class that simulates a real service retrieving records from a slow source such as a database
@Log4j2
public class StudentService {

    // Slowly returns a student by id
    public Student findById(Integer id) {
        log.info("Retrieving student with id={}", id);

        try {
            // Simulates a one second delay
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return new Student(id, "Student " + id, new Date());
    }
}
