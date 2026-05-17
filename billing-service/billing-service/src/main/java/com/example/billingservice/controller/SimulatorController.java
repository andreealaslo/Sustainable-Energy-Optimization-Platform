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
            @RequestBody(required = false) java.util.Map<String, String> payload,
            @RequestParam(value = "propertyId", required = false) String propertyIdParam,
            @RequestHeader(value = "X-Auth-User-Id", required = false) String authenticatedUserId) {

        String propertyId = null;
        if (payload != null) {
            propertyId = payload.get("propertyId");
        }
        if (propertyId == null || propertyId.isBlank()) {
            propertyId = propertyIdParam;
        }
        if (propertyId == null || propertyId.isBlank()) {
            propertyId = "simulated-property-1";
        }

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
