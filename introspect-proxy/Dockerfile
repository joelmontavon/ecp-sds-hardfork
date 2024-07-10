
FROM alpine:latest

EXPOSE 8181
WORKDIR /serve

RUN apk add --no-cache python3

COPY introspect-proxy.py /serve/introspect-proxy.py
COPY my_response_file.json /serve/response_file.json

ENTRYPOINT [  "/serve/introspect-proxy.py", "8181", "/serve/response_file.json" ]
