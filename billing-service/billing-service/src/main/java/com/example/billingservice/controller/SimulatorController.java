package com.example.billingservice.controller;

import com.example.billingservice.service.SimulatorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/billing/simulator")
public class SimulatorController {

    private final SimulatorService simulatorService;

    @Autowired
    public SimulatorController(SimulatorService simulatorService) {
        this.simulatorService = simulatorService;
    }

    @PostMapping("/start")
    public ResponseEntity<String> startSimulation(
            @RequestParam(value = "propertyId", defaultValue = "simulated-property-1") String propertyId,
            @RequestHeader(value = "X-Auth-User-Id", required = false) String authenticatedUserId) {

        simulatorService.startSimulation(propertyId);
        return ResponseEntity.ok("Simulator started for property: " + propertyId);
    }

    @PostMapping("/stop")
    public ResponseEntity<String> stopSimulation(
            @RequestHeader(value = "X-Auth-User-Id", required = false) String authenticatedUserId) {
        simulatorService.stopSimulation();
        return ResponseEntity.ok("Simulator stopped.");
    }

    @GetMapping("/status")
    public ResponseEntity<String> getStatus(
            @RequestHeader(value = "X-Auth-User-Id", required = false) String authenticatedUserId) {
        return ResponseEntity.ok(simulatorService.isRunning() ? "running" : "stopped");
    }
}
