# Using the GitHub Container Registry (ghcr.io) which is often more reliable
# for OpenFaaS images when Docker Hub has scope/connectivity issues.
FROM ghcr.io/openfaas/classic-watchdog:0.2.1 as watchdog

# Use a slim Python base
FROM python:3.11-slim

# Install watchdog into the python image
COPY --from=watchdog /fwatchdog /usr/bin/fwatchdog
RUN chmod +x /usr/bin/fwatchdog

# Set up the function directory
WORKDIR /home/app
COPY handler.py .

# Define the command the watchdog will execute for every request
ENV fprocess="python3 handler.py"

# Standard OpenFaaS watchdog port
EXPOSE 8080

# Healthcheck to ensure the watchdog is ready
HEALTHCHECK --interval=3s CMD [ -e /tmp/.lock ] || exit 1

# Set the mode to "http" or "static" (classic watchdog defaults to "streaming")
ENV mode="http"

CMD ["fwatchdog"]