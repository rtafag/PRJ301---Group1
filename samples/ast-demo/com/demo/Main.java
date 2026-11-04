package com.demo;

import com.demo.util.Calculator;
import com.demo.util.Missing;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private final Calculator calculator = new Calculator();

    public static void main(String[] args) {
        Main app = new Main();
        System.out.println(app.sum(new int[] { 1, 2, 3 }));
        app.spin();
    }

    public int sum(int[] values) {
        int total = 0;
        for (int i = 0; i < values.length; i++) {
            for (int j = 0; j < values.length; j++) {
                if (i != j && values[i] > 0 || values[j] < 0) {
                    total += calculator.add(values[i], values[j]);
                }
            }
        }
        return total;
    }

    public String find(List<String> names, String key) {
        for (String name : names) {
            if (name.equals(key)) {
                return name;
            }
        }
        return null;
    }

    public void spin() {
        int counter = 0;
        while (true) {
            counter++;
        }
    }

    public void stuck() {
        int limit = 10;
        int index = 0;
        while (index < limit) {
            System.out.println(limit);
        }
    }

    public void allocate(int n) {
        List<int[]> buffers = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            buffers.add(new int[1024]);
        }
    }

    public void safeLoop() {
        while (true) {
            if (System.currentTimeMillis() > 0) {
                break;
            }
        }
    }
}
