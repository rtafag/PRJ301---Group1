package com.group1.demo;

/**
 * DEMO SAMPLE 01 - AITA SYSTEM
 * Topic: Critical Infinite Loop Edge Cases
 * Expected Violations:
 *  - [INF_LOOP] Line 13: while(true) without break or return
 *  - [INF_LOOP] Line 20: for loop counter decremented (i--) but condition is (i < 10)
 *  - [INF_LOOP] Line 27: while condition variable 'retryCount' never updated in body
 */
public class Sample01_InfiniteLoop_Bad {

    public void processDataForever() {
        // Bug 1: while(true) with no termination
        while (true) {
            System.out.println("Processing background queue...");
        }
    }

    public void countUpWrongDirection() {
        // Bug 2: Loop counter moves in wrong direction
        for (int i = 0; i < 10; i--) {
            System.out.println("Current count: " + i);
        }
    }

    public void retryConnection() {
        // Bug 3: Condition variable never modified
        int retryCount = 0;
        while (retryCount < 5) {
            System.out.println("Attempting connection...");
            // Forgot retryCount++;
        }
    }
}
