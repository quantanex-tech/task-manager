# syntax=docker/dockerfile:1.7

FROM golang:1.23-alpine AS build
WORKDIR /src
RUN apk add --no-cache ca-certificates git
COPY go.mod ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags='-s -w' -o /out/server ./cmd/server \
    && CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags='-s -w' -o /out/migrate ./cmd/migrate \
    && CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags='-s -w' -o /out/smoke ./cmd/smoke

FROM alpine:3.22 AS runtime
RUN apk add --no-cache ca-certificates wget \
    && addgroup -S taskmanager \
    && adduser -S -G taskmanager -H -h /app taskmanager
WORKDIR /app
COPY --from=build /out/server /app/server
COPY --from=build /out/migrate /app/migrate
COPY --from=build /out/smoke /app/smoke
USER taskmanager:taskmanager
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/health/live >/dev/null || exit 1
ENTRYPOINT ["/app/server"]
