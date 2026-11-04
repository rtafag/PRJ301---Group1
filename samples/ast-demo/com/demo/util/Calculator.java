package com.demo.util;

public class Calculator implements Operation {
    public int add(int a, int b) {
        int result = a + b;
        if (result < 0) {
            result = 0;
        }
        System.out.println(result);
        return result;
    }

    public int plus(int a, int b) {
        int result = a + b;
        if (result < 0) {
            result = 0;
        }
        System.out.println(result);
        return result;
    }
}
