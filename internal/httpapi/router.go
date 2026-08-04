package httpapi

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/quantanex-tech/task-manager/internal/platform"
)

type RouterConfig struct {
	AppName     string
	Environment string
	Version     string
}

func NewRouter(cfg RouterConfig, deps *platform.Dependencies, logger *slog.Logger) http.Handler {
	router := chi.NewRouter()
	router.Use(middleware.RequestID)
	router.Use(middleware.RealIP)
	router.Use(middleware.Recoverer)
	router.Use(requestLogger(logger))

	router.Get("/health/live", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, map[string]any{
			"status":      "ok",
			"app":         cfg.AppName,
			"version":     cfg.Version,
			"environment": cfg.Environment,
		})
	})

	router.Get("/health/ready", func(w http.ResponseWriter, r *http.Request) {
		checks := deps.Readiness(r.Context())
		status := http.StatusOK
		state := "ready"
		for _, check := range checks {
			if !check.OK {
				status = http.StatusServiceUnavailable
				state = "not_ready"
			}
		}
		writeJSON(w, status, map[string]any{
			"status":      state,
			"app":         cfg.AppName,
			"version":     cfg.Version,
			"environment": cfg.Environment,
			"checks":      checks,
		})
	})

	router.Get("/", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, map[string]any{
			"app":     cfg.AppName,
			"version": cfg.Version,
			"links": map[string]string{
				"live":  "/health/live",
				"ready": "/health/ready",
			},
		})
	})

	return router
}

func requestLogger(logger *slog.Logger) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			start := time.Now()
			next.ServeHTTP(w, r)
			logger.Info("request", "method", r.Method, "path", r.URL.Path, "duration_ms", time.Since(start).Milliseconds())
		})
	}
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}
