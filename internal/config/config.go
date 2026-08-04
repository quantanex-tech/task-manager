package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
)

const Version = "0.1.0-dev"

type Config struct {
	AppName           string
	Environment       string
	HTTPAddr          string
	ServerBaseURL     string
	DatabaseURL       string
	S3Endpoint        string
	S3AccessKeyID     string
	S3SecretAccessKey string
	S3Bucket          string
	S3UseSSL          bool
}

func Load() (Config, error) {
	cfg := Config{
		AppName:           env("APP_NAME", "task-manager"),
		Environment:       env("APP_ENV", "development"),
		HTTPAddr:          env("HTTP_ADDR", ":8080"),
		ServerBaseURL:     strings.TrimRight(env("SERVER_BASE_URL", "http://server:8080"), "/"),
		DatabaseURL:       env("DATABASE_URL", "postgres://task_manager:task_manager@postgres:5432/task_manager?sslmode=disable"),
		S3Endpoint:        env("S3_ENDPOINT", "minio:9000"),
		S3AccessKeyID:     env("S3_ACCESS_KEY_ID", "task-manager-dev"),
		S3SecretAccessKey: env("S3_SECRET_ACCESS_KEY", "task-manager-dev-secret"),
		S3Bucket:          env("S3_BUCKET", "task-manager-dev"),
	}

	useSSL, err := strconv.ParseBool(env("S3_USE_SSL", "false"))
	if err != nil {
		return Config{}, fmt.Errorf("invalid S3_USE_SSL: %w", err)
	}
	cfg.S3UseSSL = useSSL

	if cfg.Environment == "production" {
		if cfg.S3SecretAccessKey == "task-manager-dev-secret" || strings.Contains(cfg.DatabaseURL, "task_manager:task_manager") {
			return Config{}, fmt.Errorf("production environment refuses development database/object-storage credentials")
		}
	}

	return cfg, nil
}

func env(key, fallback string) string {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	return value
}
