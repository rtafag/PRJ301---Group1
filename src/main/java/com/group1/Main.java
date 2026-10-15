package com.group1;

import com.group1.controller.AppController;

/**
 * Entry point for the AITA application.
 * Follows the MVC architecture: Main bootstraps and delegates execution to AppController.
 */
public class Main {
    public static void main(String[] args) {
        new AppController().run();
    }
}
