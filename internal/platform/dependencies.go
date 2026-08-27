package platform

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
	"github.com/quantanex-tech/task-manager/internal/config"
)

type Dependencies struct {
	db          *pgxpool.Pool
	objectStore *ObjectStore
}

type Check struct {
	Name    string `json:"name"`
	OK      bool   `json:"ok"`
	Message string `json:"message,omitempty"`
}

func NewDependencies(ctx context.Context, cfg config.Config) (*Dependencies, error) {
	db, err := pgxpool.New(ctx, cfg.DatabaseURL)
	if err != nil {
		return nil, fmt.Errorf("database pool: %w", err)
	}

	store, err := NewObjectStore(cfg)
	if err != nil {
		db.Close()
		return nil, err
	}

	return &Dependencies{db: db, objectStore: store}, nil
}

func (d *Dependencies) Close() {
	if d.db != nil {
		d.db.Close()
	}
}

func (d *Dependencies) Readiness(ctx context.Context) []Check {
	ctx, cancel := context.WithTimeout(ctx, 3*time.Second)
	defer cancel()

	checks := []Check{d.checkDatabase(ctx), d.checkObjectStore(ctx)}
	return checks
}

func (d *Dependencies) checkDatabase(ctx context.Context) Check {
	if err := d.db.Ping(ctx); err != nil {
		return Check{Name: "postgres", OK: false, Message: safeMessage(err)}
	}
	return Check{Name: "postgres", OK: true}
}

func (d *Dependencies) checkObjectStore(ctx context.Context) Check {
	if err := d.objectStore.Check(ctx); err != nil {
		return Check{Name: "object_storage", OK: false, Message: safeMessage(err)}
	}
	return Check{Name: "object_storage", OK: true}
}

func safeMessage(err error) string {
	message := err.Error()
	message = strings.ReplaceAll(message, "task-manager-dev-secret", "[redacted]")
	return message
}

type ObjectStore struct {
	client *minio.Client
	bucket string
}

func NewObjectStore(cfg config.Config) (*ObjectStore, error) {
	client, err := minio.New(cfg.S3Endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(cfg.S3AccessKeyID, cfg.S3SecretAccessKey, ""),
		Secure: cfg.S3UseSSL,
	})
	if err != nil {
		return nil, fmt.Errorf("object storage client: %w", err)
	}
	return &ObjectStore{client: client, bucket: cfg.S3Bucket}, nil
}

func (s *ObjectStore) EnsureBucket(ctx context.Context) error {
	exists, err := s.client.BucketExists(ctx, s.bucket)
	if err != nil {
		return fmt.Errorf("check bucket: %w", err)
	}
	if exists {
		return nil
	}
	if err := s.client.MakeBucket(ctx, s.bucket, minio.MakeBucketOptions{}); err != nil {
		return fmt.Errorf("create bucket: %w", err)
	}
	return nil
}

func (s *ObjectStore) Check(ctx context.Context) error {
	_, err := s.client.ListBuckets(ctx)
	if err != nil {
		return err
	}
	return nil
}
