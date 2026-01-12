# OpenFaaS classic watchdog as a base
FROM ghcr.io/openfaas/classic-watchdog:0.2.1 as watchdog

FROM python:3.11-slim
COPY --from=watchdog /fwatchdog /usr/bin/fwatchdog
RUN chmod +x /usr/bin/fwatchdog

WORKDIR /home/app
COPY handler.py .

ENV fprocess="python3 handler.py"
EXPOSE 8080
HEALTHCHECK --interval=3s CMD [ -e /tmp/.lock ] || exit 1
ENV mode="http"

CMD ["fwatchdog"]