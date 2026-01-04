package com.example.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Import the specific auto configuration we want to disable
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

// EXCLUDE SecurityAutoConfiguration to prevent the UserService from enforcing
// its own security and generating the default login/password.
@SpringBootApplication(exclude = {SecurityAutoConfiguration.class})
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }

}
