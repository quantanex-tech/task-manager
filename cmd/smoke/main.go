package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
	"github.com/quantanex-tech/task-manager/internal/config"
)

func main() {
	cfg, err := config.Load()
	must("load config", err)

	ctx, cancel := context.WithTimeout(context.Background(), 90*time.Second)
	defer cancel()

	waitForReady(ctx, cfg.ServerBaseURL)
	probeDatabase(ctx, cfg.DatabaseURL)
	probeObjectStorage(ctx, cfg)

	fmt.Println("smoke checks passed")
}

func waitForReady(ctx context.Context, baseURL string) {
	client := &http.Client{Timeout: 2 * time.Second}
	deadline := time.Now().Add(60 * time.Second)
	for {
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, baseURL+"/health/ready", nil)
		must("create health request", err)
		resp, err := client.Do(req)
		if err == nil && resp.Body != nil {
			body, _ := io.ReadAll(resp.Body)
			_ = resp.Body.Close()
			if resp.StatusCode == http.StatusOK && bytes.Contains(body, []byte(`"status":"ready"`)) {
				return
			}
		}
		if time.Now().After(deadline) {
			must("server readiness", fmt.Errorf("%s/health/ready did not become ready", baseURL))
		}
		select {
		case <-ctx.Done():
			must("server readiness", ctx.Err())
		case <-time.After(2 * time.Second):
		}
	}
}

func probeDatabase(ctx context.Context, databaseURL string) {
	pool, err := pgxpool.New(ctx, databaseURL)
	must("connect database", err)
	defer pool.Close()

	id := uuid.New()
	_, err = pool.Exec(ctx, "INSERT INTO infrastructure_probe(id, label) VALUES($1, $2)", id, "smoke")
	must("insert infrastructure probe", err)

	var label string
	err = pool.QueryRow(ctx, "SELECT label FROM infrastructure_probe WHERE id=$1", id).Scan(&label)
	must("read infrastructure probe", err)
	if label != "smoke" {
		must("validate infrastructure probe", fmt.Errorf("unexpected label %q", label))
	}
}

func probeObjectStorage(ctx context.Context, cfg config.Config) {
	client, err := minio.New(cfg.S3Endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(cfg.S3AccessKeyID, cfg.S3SecretAccessKey, ""),
		Secure: cfg.S3UseSSL,
	})
	must("connect object storage", err)

	name := "smoke/" + uuid.NewString() + ".json"
	payload := map[string]string{"kind": "synthetic-smoke-probe"}
	encoded, err := json.Marshal(payload)
	must("encode object probe", err)

	_, err = client.PutObject(ctx, cfg.S3Bucket, name, bytes.NewReader(encoded), int64(len(encoded)), minio.PutObjectOptions{ContentType: "application/json"})
	must("write object probe", err)

	obj, err := client.GetObject(ctx, cfg.S3Bucket, name, minio.GetObjectOptions{})
	must("read object probe", err)
	defer obj.Close()
	read, err := io.ReadAll(obj)
	must("read object body", err)
	if !bytes.Equal(read, encoded) {
		must("validate object probe", fmt.Errorf("object payload mismatch"))
	}
}

func must(step string, err error) {
	if err != nil {
		_, _ = fmt.Fprintf(os.Stderr, "%s: %v\n", step, err)
		os.Exit(1)
	}
}
