package com.group1.demo;

/**
 * DEMO SAMPLE 02 - AITA SYSTEM
 * Topic: Critical Student Logic Errors & Runtime Bugs
 * Expected Violations:
 *  - [STR_EQ] Line 15: String reference equality using '==' instead of .equals()
 *  - [ASSIGN_IN_COND] Line 21: Assignment operator '=' inside if condition
 *  - [DIV_ZERO] Line 22: Division by literal zero causing ArithmeticException
 *  - [EMPTY_CATCH] Line 28: Empty catch block swallowing runtime exception
 *  - [DEAD_CODE] Line 35: Unreachable code located after return statement
 */
public class Sample02_LogicErrors_Bad {

    public boolean authenticateUser(String inputUsername, String inputRole) {
        // Bug 1: Reference comparison on String literals
        if (inputRole == "ADMIN") {
            System.out.println("Privileged access granted.");
        }

        // Bug 2 & 3: Assignment in condition and division by zero
        boolean isVip = false;
        if (isVip = true) {
            int discountRate = 100 / 0; // Fatal runtime crash
        }

        // Bug 4: Swallowed Exception
        try {
            int parsed = Integer.parseInt("123");
        } catch (NumberFormatException e) {
            // Empty catch block
        }

        // Bug 5: Dead code after return
        return false;
        System.out.println("This statement is unreachable dead code!");
    }
}
