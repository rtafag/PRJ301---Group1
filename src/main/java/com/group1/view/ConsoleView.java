package com.group1.view;

import java.util.Scanner;

public class ConsoleView {
    private final Scanner scanner = new Scanner(System.in);

    public String chooseRole() {
        while (true) {
            System.out.println("\n=== Welcome to AITA ===");
            System.out.println("1. Student");
            System.out.println("2. Teacher");
            System.out.println("0. Exit");
            String choice = readLine("Choose your role: ").trim();
            if ("1".equals(choice))
                return "Student";
            if ("2".equals(choice))
                return "Teacher";
            if ("0".equals(choice))
                return null;
            System.out.println("Invalid option. Please choose 1, 2, or 0.");
        }
    }

    public void showGoodbye() {
        System.out.println("Goodbye!");
    }

    public void showLoginSuccessful(String username) {
        System.out.println("Login successful. Welcome, " + username + "!");
    }

    public void showUnableToLoadUsers() {
        System.out.println("Unable to load users from data/users.csv.");
    }

    public void showLoginHeader(String role) {
        System.out.println("\n=== " + role + " Login ===");
    }

    public String promptUsername() {
        return readLine("Username (0 to exit): ").trim();
    }

    public String promptPasswordHash() {
        return readLine("Password hash: ").trim();
    }

    public void showInvalidCredentials() {
        System.out.println("Invalid username, password hash, or role. Please try again.");
    }

    public int readStudentMenuOption() {
        System.out.println("\n=== Student Menu ===");
        System.out.println("1. Submit Assignment");
        System.out.println("2. Submit Source Code");
        System.out.println("3. View Processing Queue");
        System.out.println("4. Resubmit After Error");
        System.out.println("5. View Submission History");
        System.out.println("6. View My Grades");
        System.out.println("7. View Analysis Results");
        System.out.println("8. View AI Error Explanations");
        System.out.println("9. View Suggested Fixes");
        System.out.println("0. Log Out");
        return readInt("Choose an option: ", 0, 9);
    }

    public int readTeacherMenuOption() {
        System.out.println("\n=== Teacher Menu ===");
        System.out.println("1. Export Class Report (HTML / CSV)");
        System.out.println("2. Manage Class Submission List");
        System.out.println("3. View Summary Reports");
        System.out.println("4. Configure Rule Engine");
        System.out.println("5. Run Automatic Grading");
        System.out.println("6. Edit Grades");
        System.out.println("7. Provide Manual Feedback");
        System.out.println("8. Track Analysis Progress");
        System.out.println("0. Log Out");
        return readInt("Choose an option: ", 0, 8);
    }

    public int readInt(String prompt, int min, int max) {
        while (true) {
            try {
                int value = Integer.parseInt(readLine(prompt).trim());
                if (value >= min && value <= max)
                    return value;
            } catch (NumberFormatException ignored) {
            }
            System.out.printf("Enter a number from %d to %d.%n", min, max);
        }
    }

    public float readFloat(String prompt, float min, float max) {
        while (true) {
            try {
                float value = Float.parseFloat(readLine(prompt).trim());
                if (value >= min && value <= max)
                    return value;
            } catch (NumberFormatException ignored) {
            }
            System.out.printf("Enter a number from %.1f to %.1f.%n", min, max);
        }
    }

    public String readRequired(String prompt) {
        while (true) {
            String value = readLine(prompt).trim();
            if (!value.isEmpty())
                return value;
            System.out.println("This value is required.");
        }
    }

    public String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine();
    }
}
